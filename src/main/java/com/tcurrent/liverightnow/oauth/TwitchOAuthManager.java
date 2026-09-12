package com.tcurrent.liverightnow.oauth;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.tcurrent.liverightnow.LiveRightNowConfig;

import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.LinkBrowser;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Singleton
public class TwitchOAuthManager
{
    private static final Logger log = LoggerFactory.getLogger(TwitchOAuthManager.class);
    private static final String TWITCH_DEVICE_URL = "https://id.twitch.tv/oauth2/device";
    private static final String TWITCH_TOKEN_URL = "https://id.twitch.tv/oauth2/token";
    private static final String TWITCH_VALIDATE_URL = "https://id.twitch.tv/oauth2/validate";

    private final LiveRightNowConfig config;
    private final ConfigManager configManager;
    private final OkHttpClient okHttpClient;
    private final Gson gson;
    private final ScheduledExecutorService executorService;
    private final Notifier notifier;
    private ScheduledFuture<?> tokenPollTask;

    @Inject
    public TwitchOAuthManager(
        LiveRightNowConfig config,
        ConfigManager configManager,
        OkHttpClient okHttpClient,
        Gson gson,
        ScheduledExecutorService executorService,
        Notifier notifier
    )
    {
        this.config = config;
        this.configManager = configManager;
        this.okHttpClient = okHttpClient;
        this.gson = gson;
        this.executorService = executorService;
        this.notifier = notifier;
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
        String clientId = config.twitchClientId();

        Request request = new Request.Builder()
            .url(TWITCH_DEVICE_URL)
            .post(new FormBody.Builder()
                .add("client_id", clientId)
                .add("scopes", "user:read:follows")
                .build())
            .build();

        executorService.execute(() -> {
            try (Response response = okHttpClient.newCall(request).execute())
            {
                ResponseBody body = response.body();
                JsonObject json = body == null ? null : gson.fromJson(body.charStream(), JsonObject.class);
                if (!response.isSuccessful() || json == null || !json.has("device_code"))
                {
                    log.warn("Twitch device authorization request failed with status {}", response.code());
                    resultFuture.complete(false);
                    return;
                }

                String deviceCode = json.get("device_code").getAsString();
                String userCode = json.get("user_code").getAsString();
                String verificationUri = json.get("verification_uri").getAsString();
                int interval = json.has("interval") ? json.get("interval").getAsInt() : 5;
                int expiresIn = json.has("expires_in") ? json.get("expires_in").getAsInt() : 600;

                notifier.notify("Twitch code: " + userCode);
                LinkBrowser.browse(verificationUri);
                pollForToken(clientId, deviceCode, interval, expiresIn, resultFuture);
            }
            catch (IOException | RuntimeException e)
            {
                log.error("Twitch device authorization failed", e);
                resultFuture.complete(false);
            }
        });

        return resultFuture;
    }

    public void disconnect()
    {
        if (tokenPollTask != null)
        {
            tokenPollTask.cancel(true);
            tokenPollTask = null;
        }

        configManager.unsetConfiguration(LiveRightNowConfig.GROUP, LiveRightNowConfig.TWITCH_OAUTH_TOKEN_KEY);
        configManager.unsetConfiguration(LiveRightNowConfig.GROUP, LiveRightNowConfig.TWITCH_CONNECTED_USER_KEY);
    }

    public void handleTokenExpired()
    {
        log.warn("Twitch token has expired or is invalid. Disconnecting session.");
        disconnect();
    }

    private void pollForToken(String clientId, String deviceCode, int interval, int expiresIn, CompletableFuture<Boolean> resultFuture)
    {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(expiresIn);
        tokenPollTask = executorService.scheduleWithFixedDelay(() -> {
            if (System.currentTimeMillis() >= deadline || resultFuture.isDone())
            {
                tokenPollTask.cancel(false);
                resultFuture.complete(false);
                return;
            }

            Request request = new Request.Builder()
                .url(TWITCH_TOKEN_URL)
                .post(new FormBody.Builder()
                    .add("client_id", clientId)
                    .add("device_code", deviceCode)
                    .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                    .build())
                .build();

            try (Response response = okHttpClient.newCall(request).execute())
            {
                ResponseBody body = response.body();
                JsonObject json = body == null ? null : gson.fromJson(body.charStream(), JsonObject.class);
                if (response.isSuccessful() && json != null && json.has("access_token"))
                {
                    tokenPollTask.cancel(false);
                    String token = json.get("access_token").getAsString();
                    saveToken(token);
                    fetchAndSaveTwitchUser(token);
                    resultFuture.complete(true);
                }
                else if (json != null && json.has("message") && !"authorization_pending".equals(json.get("message").getAsString()))
                {
                    log.warn("Twitch device authorization response: {}", json);
                }
            }
            catch (IOException | RuntimeException e)
            {
                log.warn("Twitch device token poll failed", e);
            }
        }, Math.max(1, interval), Math.max(1, interval), TimeUnit.SECONDS);
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
