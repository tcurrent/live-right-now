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
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Singleton
public class KickOAuthManager
{
    private static final Logger log = LoggerFactory.getLogger(KickOAuthManager.class);
    private static final String KICK_USERS_URL = "https://api.kick.com/public/v1/users";
    private static final String KICK_CHANNELS_URL = "https://api.kick.com/public/v1/channels";
    private static final String KICK_REVOKE_URL = "https://id.kick.com/oauth/revoke";
    private static final String KICK_AUTHORIZE_URL = "https://id.kick.com/oauth/authorize";
    private static final String KICK_REDIRECT_URI = "http://localhost:4646/callback";
    private static final String EXCHANGE_URL = "https://live-right-now-oauth-proxy.tcurrent.workers.dev/exchange";
    private static final String HANDOFF_URL = "https://live-right-now-oauth-proxy.tcurrent.workers.dev/handoff";
    private static final String REFRESH_URL = "https://live-right-now-oauth-proxy.tcurrent.workers.dev/refresh";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final LiveRightNowConfig config;
    private final ConfigManager configManager;
    private final OkHttpClient okHttpClient;
    private final Gson gson;
    private final ScheduledExecutorService executorService;
    private final OAuthLoopbackServer loopbackServer;
    private volatile String lastKnownToken;
    private volatile String lastKnownRefreshToken;

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
        this.lastKnownToken = config.kickOAuthToken();
        this.lastKnownRefreshToken = config.kickRefreshToken();
    }

    public boolean isConnected()
    {
        String token = config.kickOAuthToken();
        boolean connected = token != null && !token.trim().isEmpty();
        if (connected)
        {
            lastKnownToken = token;
        }
        return connected;
    }

    public String getConnectedUser()
    {
        return config.kickConnectedUser();
    }

    public CompletableFuture<Boolean> startConnectFlow()
    {
        disconnect();
        CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();
        OAuthFlow flow = OAuthFlow.create();

        try
        {
            loopbackServer.startKick(flow.getState(), (provider, authorizationCode) -> {
                if ("kick".equalsIgnoreCase(provider))
                {
                    executorService.execute(() -> {
                        String handoffCode = exchangeAuthorizationCode(
                            authorizationCode,
                            flow.getState(),
                            flow.getHandoffProof(),
                            flow.getCodeVerifier()
                        );
                        JsonObject tokens = handoffCode == null ? null : redeemHandoff(handoffCode, flow.getHandoffSecret());
                        if (tokens == null)
                        {
                            resultFuture.complete(false);
                            return;
                        }
                        String token = tokens.get("access_token").getAsString();
                        saveTokens(token, tokens);
                        fetchAndSaveKickUser(token);
                        resultFuture.complete(true);
                    });
                }
            });

            HttpUrl authorizeUrl = HttpUrl.parse(KICK_AUTHORIZE_URL).newBuilder()
                .addQueryParameter("response_type", "code")
                .addQueryParameter("client_id", config.kickClientId().trim())
                .addQueryParameter("redirect_uri", KICK_REDIRECT_URI)
                .addQueryParameter("scope", "user:read channel:read")
                .addQueryParameter("code_challenge", flow.getCodeChallenge())
                .addQueryParameter("code_challenge_method", "S256")
                .addQueryParameter("state", flow.getState())
                .build();
            LinkBrowser.browse(authorizeUrl.toString());
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
        String token = config.kickOAuthToken();
        String refreshToken = config.kickRefreshToken();
        lastKnownToken = null;
        lastKnownRefreshToken = null;
        configManager.unsetConfiguration(LiveRightNowConfig.GROUP, LiveRightNowConfig.KICK_REFRESH_TOKEN_KEY);
        configManager.unsetConfiguration(LiveRightNowConfig.GROUP, LiveRightNowConfig.KICK_OAUTH_TOKEN_KEY);
        configManager.unsetConfiguration(LiveRightNowConfig.GROUP, LiveRightNowConfig.KICK_CONNECTED_USER_KEY);
        revokeTokens(token, refreshToken);
    }

    public void handleConfigurationReset()
    {
        String token = lastKnownToken;
        String refreshToken = lastKnownRefreshToken;
        if (token == null || !config.kickOAuthToken().trim().isEmpty())
        {
            return;
        }

        lastKnownToken = null;
        lastKnownRefreshToken = null;
        revokeTokens(token, refreshToken);
    }

    private void revokeTokens(String token, String refreshToken)
    {
        if (token != null && !token.trim().isEmpty())
        {
            executorService.execute(() -> revokeToken(token, "access_token"));
        }
        if (refreshToken != null && !refreshToken.trim().isEmpty())
        {
            executorService.execute(() -> revokeToken(refreshToken, "refresh_token"));
        }
    }

    public void handleTokenExpired()
    {
        log.warn("Kick token has expired or is invalid. Disconnecting session.");
        disconnect();
    }

    /**
     * Returns the new access token, or null on failure. Disconnects only if Kick rejected the refresh token.
     */
    public synchronized String refreshAccessToken(String failedToken)
    {
        String current = config.kickOAuthToken();
        if (current != null && !current.trim().isEmpty() && !current.trim().equals(failedToken))
        {
            return current.trim();
        }

        String refreshToken = config.kickRefreshToken();
        if (refreshToken == null || refreshToken.trim().isEmpty())
        {
            handleTokenExpired();
            return null;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("provider", "kick");
        payload.addProperty("refresh_token", refreshToken.trim());
        RequestBody body = RequestBody.create(JSON, payload.toString());
        Request request = new Request.Builder().url(REFRESH_URL).post(body).build();

        try (Response response = okHttpClient.newCall(request).execute())
        {
            ResponseBody responseBody = response.body();
            JsonObject json = responseBody == null ? null : gson.fromJson(responseBody.charStream(), JsonObject.class);
            if (response.isSuccessful() && json != null && json.has("access_token"))
            {
                String token = json.get("access_token").getAsString();
                saveTokens(token, json);
                return token;
            }
            if (response.code() >= 400 && response.code() < 500)
            {
                handleTokenExpired();
            }
            else
            {
                log.warn("Kick token refresh failed with status {}", response.code());
            }
        }
        catch (IOException | RuntimeException e)
        {
            log.warn("Failed to refresh Kick token", e);
        }
        return null;
    }

    private void saveTokens(String token, JsonObject json)
    {
        lastKnownToken = token;
        if (json.has("refresh_token") && !json.get("refresh_token").isJsonNull())
        {
            String refreshToken = json.get("refresh_token").getAsString();
            lastKnownRefreshToken = refreshToken;
            configManager.setConfiguration(
                LiveRightNowConfig.GROUP,
                LiveRightNowConfig.KICK_REFRESH_TOKEN_KEY,
                refreshToken
            );
        }
        configManager.setConfiguration(LiveRightNowConfig.GROUP, LiveRightNowConfig.KICK_OAUTH_TOKEN_KEY, token);
    }

    private JsonObject redeemHandoff(String handoffCode, String handoffSecret)
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
                return json;
            }
        }
        catch (IOException | RuntimeException e)
        {
            log.warn("Failed to redeem Kick OAuth handoff", e);
        }
        return null;
    }

    private String exchangeAuthorizationCode(String authorizationCode, String state, String handoffProof, String codeVerifier)
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("provider", "kick");
        payload.addProperty("code", authorizationCode);
        payload.addProperty("state", state);
        payload.addProperty("handoff_proof", handoffProof);
        payload.addProperty("code_verifier", codeVerifier);
        payload.addProperty("redirect_uri", KICK_REDIRECT_URI);
        RequestBody body = RequestBody.create(JSON, payload.toString());
        Request request = new Request.Builder().url(EXCHANGE_URL).post(body).build();

        try (Response response = okHttpClient.newCall(request).execute())
        {
            ResponseBody responseBody = response.body();
            JsonObject json = responseBody == null ? null : gson.fromJson(responseBody.charStream(), JsonObject.class);
            if (response.isSuccessful() && json != null && json.has("handoff_code"))
            {
                return json.get("handoff_code").getAsString();
            }
        }
        catch (IOException | RuntimeException e)
        {
            log.warn("Failed to exchange Kick authorization code", e);
        }
        return null;
    }

    private void revokeToken(String token, String tokenType)
    {
        HttpUrl baseUrl = HttpUrl.parse(KICK_REVOKE_URL);
        if (baseUrl == null)
        {
            return;
        }
        HttpUrl revokeUrl = baseUrl.newBuilder()
            .addQueryParameter("token", token.trim())
            .addQueryParameter("token_hint_type", tokenType)
            .build();
        RequestBody body = RequestBody.create(null, new byte[0]);
        Request request = new Request.Builder().url(revokeUrl).post(body).build();
        try (Response response = okHttpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                log.warn("Kick token revocation returned status {}", response.code());
            }
        }
        catch (IOException | RuntimeException e)
        {
            log.warn("Failed to revoke Kick token", e);
        }
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
