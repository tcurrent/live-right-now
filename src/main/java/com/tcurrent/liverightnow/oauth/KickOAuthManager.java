package com.tcurrent.liverightnow.oauth;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.tcurrent.liverightnow.LiveRightNowConfig;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.LinkBrowser;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Singleton
public class KickOAuthManager
{
    private static final Logger log = LoggerFactory.getLogger(KickOAuthManager.class);
    private static final String KICK_AUTH_URL = "https://id.kick.com/oauth/authorize";
    private static final String KICK_USER_API_URL = "https://kick.com/api/v2/user";

    private final LiveRightNowConfig config;
    private final ConfigManager configManager;
    private final OkHttpClient okHttpClient;
    private final Gson gson;

    private OAuthLoopbackServer activeServer;

    @Inject
    public KickOAuthManager(LiveRightNowConfig config, ConfigManager configManager, OkHttpClient okHttpClient, Gson gson)
    {
        this.config = config;
        this.configManager = configManager;
        this.okHttpClient = okHttpClient;
        this.gson = gson;
    }

    public boolean isConnected()
    {
        String token = config.kickOAuthToken();
        return token != null && !token.trim().isEmpty();
    }

    public String getConnectedUser()
    {
        return config.kickConnectedUser();
    }

    public CompletableFuture<Boolean> startConnectFlow()
    {
        disconnect();

        activeServer = new OAuthLoopbackServer();
        CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();

        activeServer.startServer(port -> {
            String redirectUri = "http://localhost:" + port + "/callback";
            HttpUrl baseAuthUrl = HttpUrl.parse(KICK_AUTH_URL);
            if (baseAuthUrl == null)
            {
                resultFuture.complete(false);
                return;
            }

            HttpUrl authUrl = baseAuthUrl.newBuilder()
                .addQueryParameter("redirect_uri", redirectUri)
                .addQueryParameter("response_type", "token")
                .addQueryParameter("client_id", "liverightnow-runelite")
                .build();

            log.info("Opening Kick OAuth browser authorization URL");
            LinkBrowser.open(authUrl.toString());
        }).thenAccept(params -> {
            String token = params.get("access_token");
            if (token == null)
            {
                token = params.get("code");
            }

            if (token != null && !token.isEmpty())
            {
                saveToken(token);
                fetchAndSaveKickUser(token);
                resultFuture.complete(true);
            }
            else
            {
                log.warn("Kick OAuth callback did not contain token/code: {}", params);
                resultFuture.complete(false);
            }
        }).exceptionally(ex -> {
            log.error("Kick OAuth flow encountered an error", ex);
            resultFuture.complete(false);
            return null;
        });

        return resultFuture;
    }

    public void disconnect()
    {
        if (activeServer != null)
        {
            activeServer.stop();
            activeServer = null;
        }

        configManager.unsetConfiguration(LiveRightNowConfig.GROUP, LiveRightNowConfig.KICK_OAUTH_TOKEN_KEY);
        configManager.unsetConfiguration(LiveRightNowConfig.GROUP, LiveRightNowConfig.KICK_CONNECTED_USER_KEY);
        log.info("Disconnected Kick account");
    }

    public void handleTokenExpired()
    {
        log.warn("Kick token has expired or is invalid. Disconnecting session.");
        disconnect();
    }

    private void saveToken(String token)
    {
        configManager.setConfiguration(LiveRightNowConfig.GROUP, LiveRightNowConfig.KICK_OAUTH_TOKEN_KEY, token);
    }

    private void fetchAndSaveKickUser(String token)
    {
        Request request = new Request.Builder()
            .url(KICK_USER_API_URL)
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/json")
            .get()
            .build();

        try (Response response = okHttpClient.newCall(request).execute())
        {
            if (response.isSuccessful())
            {
                ResponseBody body = response.body();
                if (body != null)
                {
                    JsonObject json = gson.fromJson(body.charStream(), JsonObject.class);
                    if (json != null && json.has("username"))
                    {
                        String username = json.get("username").getAsString();
                        configManager.setConfiguration(LiveRightNowConfig.GROUP, LiveRightNowConfig.KICK_CONNECTED_USER_KEY, username);
                        log.info("Kick account connected successfully for user: {}", username);
                        return;
                    }
                }
            }
        }
        catch (IOException e)
        {
            log.debug("Could not fetch Kick user details via token", e);
        }

        // Fallback to "Connected" placeholder if user endpoint isn't accessible
        configManager.setConfiguration(LiveRightNowConfig.GROUP, LiveRightNowConfig.KICK_CONNECTED_USER_KEY, "Connected");
    }
}
