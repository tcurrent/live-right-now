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
public class TwitchOAuthManager
{
    private static final Logger log = LoggerFactory.getLogger(TwitchOAuthManager.class);
    private static final String TWITCH_AUTH_URL = "https://id.twitch.tv/oauth2/authorize";
    private static final String TWITCH_VALIDATE_URL = "https://id.twitch.tv/oauth2/validate";

    private final LiveRightNowConfig config;
    private final ConfigManager configManager;
    private final OkHttpClient okHttpClient;
    private final Gson gson;

    private OAuthLoopbackServer activeServer;

    @Inject
    public TwitchOAuthManager(LiveRightNowConfig config, ConfigManager configManager, OkHttpClient okHttpClient, Gson gson)
    {
        this.config = config;
        this.configManager = configManager;
        this.okHttpClient = okHttpClient;
        this.gson = gson;
    }

    public boolean isConnected()
    {
        String token = config.twitchOAuthToken();
        return token != null && !token.trim().isEmpty();
    }

    public String getConnectedUser()
    {
        return config.twitchConnectedUser();
    }

    public CompletableFuture<Boolean> startConnectFlow()
    {
        disconnect();

        activeServer = new OAuthLoopbackServer();
        CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();

        activeServer.startServer(port -> {
            String clientId = config.twitchClientId();
            if (clientId == null || clientId.trim().isEmpty())
            {
                clientId = "kimne78kx3ncx6brgo4mv6wki5h1ko";
            }

            String redirectUri = "http://localhost:" + port + "/callback";
            HttpUrl baseAuthUrl = HttpUrl.parse(TWITCH_AUTH_URL);
            if (baseAuthUrl == null)
            {
                resultFuture.complete(false);
                return;
            }

            HttpUrl authUrl = baseAuthUrl.newBuilder()
                .addQueryParameter("client_id", clientId)
                .addQueryParameter("redirect_uri", redirectUri)
                .addQueryParameter("response_type", "token")
                .addQueryParameter("scope", "user:read:follows")
                .build();

            log.info("Opening Twitch OAuth browser authorization URL");
            LinkBrowser.open(authUrl.toString());
        }).thenAccept(params -> {
            String token = params.get("access_token");
            if (token != null && !token.isEmpty())
            {
                saveToken(token);
                fetchAndSaveTwitchUser(token);
                resultFuture.complete(true);
            }
            else
            {
                log.warn("Twitch OAuth callback did not contain access token: {}", params);
                resultFuture.complete(false);
            }
        }).exceptionally(ex -> {
            log.error("Twitch OAuth flow encountered an error", ex);
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

        configManager.unsetConfiguration(LiveRightNowConfig.GROUP, LiveRightNowConfig.TWITCH_OAUTH_TOKEN_KEY);
        configManager.unsetConfiguration(LiveRightNowConfig.GROUP, LiveRightNowConfig.TWITCH_CONNECTED_USER_KEY);
        log.info("Disconnected Twitch account");
    }

    public void handleTokenExpired()
    {
        log.warn("Twitch token has expired or is invalid. Disconnecting session.");
        disconnect();
    }

    private void saveToken(String token)
    {
        configManager.setConfiguration(LiveRightNowConfig.GROUP, LiveRightNowConfig.TWITCH_OAUTH_TOKEN_KEY, token);
    }

    private void fetchAndSaveTwitchUser(String token)
    {
        Request request = new Request.Builder()
            .url(TWITCH_VALIDATE_URL)
            .header("Authorization", "OAuth " + token)
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
                    if (json != null && json.has("login"))
                    {
                        String username = json.get("login").getAsString();
                        configManager.setConfiguration(LiveRightNowConfig.GROUP, LiveRightNowConfig.TWITCH_CONNECTED_USER_KEY, username);
                        log.info("Twitch account connected successfully for user: {}", username);
                    }
                }
            }
        }
        catch (IOException e)
        {
            log.warn("Failed to validate Twitch token", e);
        }
    }
}
