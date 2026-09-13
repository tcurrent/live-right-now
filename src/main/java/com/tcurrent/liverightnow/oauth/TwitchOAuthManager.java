package com.tcurrent.liverightnow.oauth;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.tcurrent.liverightnow.LiveRightNowConfig;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.LinkBrowser;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Singleton
public class TwitchOAuthManager
{
    private static final Logger log = LoggerFactory.getLogger(TwitchOAuthManager.class);
    private static final String TWITCH_VALIDATE_URL = "https://id.twitch.tv/oauth2/validate";
    private static final String TWITCH_REVOKE_URL = "https://id.twitch.tv/oauth2/revoke";
    private static final String PROXY_URL = "https://tcurrent.github.io/live-right-now-oauth-proxy/";
    private static final String HANDOFF_URL = "https://live-right-now-oauth-proxy.tcurrent.workers.dev/handoff";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final LiveRightNowConfig config;
    private final ConfigManager configManager;
    private final OkHttpClient okHttpClient;
    private final Gson gson;
    private final ScheduledExecutorService executorService;
    private final OAuthLoopbackServer loopbackServer;

    @Inject
    public TwitchOAuthManager(
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
        CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();
        OAuthFlow flow = OAuthFlow.create();

        try
        {
            loopbackServer.start("twitch", flow.getState(), (provider, handoffCode) -> {
                if ("twitch".equalsIgnoreCase(provider))
                {
                    executorService.execute(() -> {
                        String token = redeemHandoff(handoffCode, flow.getHandoffSecret());
                        if (token == null)
                        {
                            resultFuture.complete(false);
                            return;
                        }
                        saveToken(token);
                        fetchAndSaveTwitchUser(token);
                        resultFuture.complete(true);
                    });
                }
            });

            String proxyUrl = PROXY_URL + "?provider=twitch&state=" + flow.getState()
                + "&handoff_proof=" + flow.getHandoffProof();
            LinkBrowser.browse(proxyUrl);
        }
        catch (IOException e)
        {
            log.error("Failed to start OAuth loopback server for Twitch", e);
            resultFuture.complete(false);
        }

        return resultFuture;
    }

    public void disconnect()
    {
        String token = config.twitchOAuthToken();
        configManager.unsetConfiguration(LiveRightNowConfig.GROUP, LiveRightNowConfig.TWITCH_OAUTH_TOKEN_KEY);
        configManager.unsetConfiguration(LiveRightNowConfig.GROUP, LiveRightNowConfig.TWITCH_CONNECTED_USER_KEY);
        if (token != null && !token.trim().isEmpty())
        {
            executorService.execute(() -> revokeToken(token));
        }
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

    private String redeemHandoff(String handoffCode, String handoffSecret)
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("handoff_code", handoffCode);
        payload.addProperty("handoff_secret", handoffSecret);
        RequestBody body = RequestBody.create(JSON, payload.toString());
        Request request = new Request.Builder().url(HANDOFF_URL).post(body).build();

        try (Response response = okHttpClient.newCall(request).execute())
        {
            ResponseBody responseBody = response.body();
            JsonObject json = responseBody == null ? null : gson.fromJson(responseBody.charStream(), JsonObject.class);
            if (response.isSuccessful() && json != null && json.has("access_token"))
            {
                return json.get("access_token").getAsString();
            }
        }
        catch (IOException | RuntimeException e)
        {
            log.warn("Failed to redeem Twitch OAuth handoff", e);
        }
        return null;
    }

    private void revokeToken(String token)
    {
        RequestBody body = new FormBody.Builder()
            .add("client_id", config.twitchClientId().trim())
            .add("token", token.trim())
            .build();
        Request request = new Request.Builder().url(TWITCH_REVOKE_URL).post(body).build();
        try (Response response = okHttpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                log.warn("Twitch token revocation returned status {}", response.code());
            }
        }
        catch (IOException | RuntimeException e)
        {
            log.warn("Failed to revoke Twitch token", e);
        }
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
            ResponseBody body = response.body();
            JsonObject json = body == null ? null : gson.fromJson(body.charStream(), JsonObject.class);
            if (response.isSuccessful() && json != null && json.has("login"))
            {
                configManager.setConfiguration(
                    LiveRightNowConfig.GROUP,
                    LiveRightNowConfig.TWITCH_CONNECTED_USER_KEY,
                    json.get("login").getAsString()
                );
            }
        }
        catch (IOException | RuntimeException e)
        {
            log.warn("Failed to validate Twitch token", e);
        }
    }
}
