package com.tcurrent.liverightnow.oauth;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tcurrent.liverightnow.LiveRightNowConfig;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.LinkBrowser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Singleton
public class KickOAuthManager
{
    private static final Logger log = LoggerFactory.getLogger(KickOAuthManager.class);
    private static final String KICK_USERS_URL = "https://api.kick.com/public/v1/users";
    private static final String KICK_CHANNELS_URL = "https://api.kick.com/public/v1/channels";

    private final LiveRightNowConfig config;
    private final ConfigManager configManager;
    private final OkHttpClient okHttpClient;
    private final Gson gson;
    private final ScheduledExecutorService executorService;
    private final OAuthLoopbackServer loopbackServer;

    @Inject
    public KickOAuthManager(
        LiveRightNowConfig config,
        ConfigManager configManager,
        OkHttpClient okHttpClient,
        Gson gson,
        ScheduledExecutorService executorService,
        OAuthLoopbackServer loopbackServer
    )
    {
        this.config = config;
        this.configManager = configManager;
        this.okHttpClient = okHttpClient;
        this.gson = gson;
        this.executorService = executorService;
        this.loopbackServer = loopbackServer;
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
        CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();

        try
        {
            loopbackServer.start((provider, token) -> {
                if ("kick".equalsIgnoreCase(provider))
                {
                    saveToken(token);
                    executorService.execute(() -> {
                        fetchAndSaveKickUser(token);
                        resultFuture.complete(true);
                    });
                }
            });

            String proxyUrl = "https://tcurrent.github.io/live-right-now-oauth-proxy/?provider=kick&port=" + OAuthLoopbackServer.PORT;
            LinkBrowser.browse(proxyUrl);
        }
        catch (IOException e)
        {
            log.error("Failed to start OAuth loopback server for Kick", e);
            resultFuture.complete(false);
        }

        return resultFuture;
    }

    public void disconnect()
    {
        configManager.unsetConfiguration(LiveRightNowConfig.GROUP, LiveRightNowConfig.KICK_OAUTH_TOKEN_KEY);
        configManager.unsetConfiguration(LiveRightNowConfig.GROUP, LiveRightNowConfig.KICK_CONNECTED_USER_KEY);
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
            .url(KICK_USERS_URL)
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/json")
            .get()
            .build();

        try (Response response = okHttpClient.newCall(request).execute())
        {
            ResponseBody body = response.body();
            JsonObject json = body == null ? null : gson.fromJson(body.charStream(), JsonObject.class);
            if (response.isSuccessful() && json != null && json.has("data"))
            {
                JsonElement dataElem = json.get("data");
                String username = extractUsername(dataElem);
                if (username != null && !username.isEmpty())
                {
                    configManager.setConfiguration(
                        LiveRightNowConfig.GROUP,
                        LiveRightNowConfig.KICK_CONNECTED_USER_KEY,
                        username
                    );
                    return;
                }
            }
        }
        catch (IOException | RuntimeException e)
        {
            log.warn("Failed to fetch Kick user profile from users endpoint, trying channels endpoint", e);
        }

        Request channelRequest = new Request.Builder()
            .url(KICK_CHANNELS_URL)
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/json")
            .get()
            .build();

        try (Response response = okHttpClient.newCall(channelRequest).execute())
        {
            ResponseBody body = response.body();
            JsonObject json = body == null ? null : gson.fromJson(body.charStream(), JsonObject.class);
            if (response.isSuccessful() && json != null && json.has("data"))
            {
                JsonElement dataElem = json.get("data");
                String username = extractUsername(dataElem);
                if (username != null && !username.isEmpty())
                {
                    configManager.setConfiguration(
                        LiveRightNowConfig.GROUP,
                        LiveRightNowConfig.KICK_CONNECTED_USER_KEY,
                        username
                    );
                }
            }
        }
        catch (IOException | RuntimeException e)
        {
            log.warn("Failed to fetch Kick user profile from channels endpoint", e);
        }
    }

    private String extractUsername(JsonElement dataElem)
    {
        if (dataElem.isJsonArray())
        {
            JsonArray arr = dataElem.getAsJsonArray();
            if (arr.size() > 0 && arr.get(0).isJsonObject())
            {
                JsonObject obj = arr.get(0).getAsJsonObject();
                if (obj.has("name") && !obj.get("name").isJsonNull())
                {
                    return obj.get("name").getAsString();
                }
                if (obj.has("username") && !obj.get("username").isJsonNull())
                {
                    return obj.get("username").getAsString();
                }
                if (obj.has("slug") && !obj.get("slug").isJsonNull())
                {
                    return obj.get("slug").getAsString();
                }
            }
        }
        else if (dataElem.isJsonObject())
        {
            JsonObject obj = dataElem.getAsJsonObject();
            if (obj.has("name") && !obj.get("name").isJsonNull())
            {
                return obj.get("name").getAsString();
            }
            if (obj.has("username") && !obj.get("username").isJsonNull())
            {
                return obj.get("username").getAsString();
            }
            if (obj.has("slug") && !obj.get("slug").isJsonNull())
            {
                return obj.get("slug").getAsString();
            }
        }
        return null;
    }
}
