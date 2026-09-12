package com.tcurrent.liverightnow.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tcurrent.liverightnow.LiveRightNowConfig;
import com.tcurrent.liverightnow.model.Platform;
import com.tcurrent.liverightnow.model.StreamInfo;
import com.tcurrent.liverightnow.notification.StreamNotificationManager;
import com.tcurrent.liverightnow.oauth.TwitchOAuthManager;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Singleton
public class TwitchService
{
    private static final Logger log = LoggerFactory.getLogger(TwitchService.class);
    private static final String HELIX_STREAMS_URL = "https://api.twitch.tv/helix/streams";

    private final OkHttpClient okHttpClient;
    private final Gson gson;
    private final LiveRightNowConfig config;
    private final TwitchOAuthManager twitchOAuthManager;
    private final StreamNotificationManager notificationManager;

    @Inject
    public TwitchService(
        OkHttpClient okHttpClient,
        Gson gson,
        LiveRightNowConfig config,
        TwitchOAuthManager twitchOAuthManager,
        StreamNotificationManager notificationManager
    )
    {
        this.okHttpClient = okHttpClient;
        this.gson = gson;
        this.config = config;
        this.twitchOAuthManager = twitchOAuthManager;
        this.notificationManager = notificationManager;
    }

    public List<StreamInfo> fetchStreams(List<String> usernames)
    {
        if (usernames == null || usernames.isEmpty())
        {
            return Collections.emptyList();
        }

        String clientId = config.twitchClientId() != null ? config.twitchClientId().trim() : "";
        String token = config.twitchOAuthToken() != null ? config.twitchOAuthToken().trim() : "";

        if (clientId.isEmpty() || token.isEmpty())
        {
            log.warn("Twitch Client ID or OAuth Token is not configured. Skipping Twitch lookup.");
            return Collections.emptyList();
        }

        // Strip "Bearer " prefix if user included it in config
        if (token.toLowerCase().startsWith("bearer "))
        {
            token = token.substring(7).trim();
        }

        HttpUrl baseUrl = HttpUrl.parse(HELIX_STREAMS_URL);
        if (baseUrl == null)
        {
            return Collections.emptyList();
        }

        HttpUrl.Builder urlBuilder = baseUrl.newBuilder();
        for (String user : usernames)
        {
            String trimmed = user.trim().toLowerCase();
            if (!trimmed.isEmpty())
            {
                urlBuilder.addQueryParameter("user_login", trimmed);
            }
        }

        Request request = new Request.Builder()
            .url(urlBuilder.build())
            .header("Client-Id", clientId)
            .header("Authorization", "Bearer " + token)
            .get()
            .build();

        try (Response response = okHttpClient.newCall(request).execute())
        {
            if (response.code() == 401)
            {
                log.warn("Twitch API returned 401 Unauthorized (OAuth token expired).");
                twitchOAuthManager.handleTokenExpired();
                notificationManager.notifySessionExpired(Platform.TWITCH);
                return Collections.emptyList();
            }

            if (!response.isSuccessful())
            {
                log.warn("Twitch API returned unsuccessful response code: {}", response.code());
                return Collections.emptyList();
            }

            ResponseBody body = response.body();
            if (body == null)
            {
                return Collections.emptyList();
            }

            JsonObject json = gson.fromJson(body.charStream(), JsonObject.class);
            if (json == null || !json.has("data"))
            {
                return Collections.emptyList();
            }

            Map<String, StreamInfo> liveMap = new HashMap<>();
            JsonArray dataArray = json.getAsJsonArray("data");
            for (JsonElement element : dataArray)
            {
                if (!element.isJsonObject())
                {
                    continue;
                }

                JsonObject streamObj = element.getAsJsonObject();
                String login = streamObj.has("user_login") ? streamObj.get("user_login").getAsString() : "";
                String type = streamObj.has("type") ? streamObj.get("type").getAsString() : "";
                boolean isLive = "live".equalsIgnoreCase(type);
                String title = streamObj.has("title") ? streamObj.get("title").getAsString() : "";
                String gameName = streamObj.has("game_name") ? streamObj.get("game_name").getAsString() : "";
                int viewerCount = streamObj.has("viewer_count") ? streamObj.get("viewer_count").getAsInt() : 0;
                String streamId = streamObj.has("id") ? streamObj.get("id").getAsString() : "";

                if (!login.isEmpty())
                {
                    liveMap.put(login.toLowerCase(), new StreamInfo(Platform.TWITCH, login, isLive, title, gameName, viewerCount, streamId));
                }
            }

            List<StreamInfo> results = new ArrayList<>();
            for (String user : usernames)
            {
                String lower = user.trim().toLowerCase();
                if (lower.isEmpty())
                {
                    continue;
                }

                StreamInfo stream = liveMap.get(lower);
                if (stream != null)
                {
                    results.add(stream);
                }
                else
                {
                    results.add(StreamInfo.offline(Platform.TWITCH, user.trim()));
                }
            }

            return results;
        }
        catch (IOException | RuntimeException e)
        {
            log.error("Failed to query Twitch streams", e);
            return Collections.emptyList();
        }
    }
}
