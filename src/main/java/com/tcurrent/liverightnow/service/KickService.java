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
import com.tcurrent.liverightnow.oauth.KickOAuthManager;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Singleton
public class KickService
{
    private static final Logger log = LoggerFactory.getLogger(KickService.class);
    private static final String KICK_CHANNELS_API_URL = "https://api.kick.com/public/v1/channels";

    private final OkHttpClient okHttpClient;
    private final Gson gson;
    private final LiveRightNowConfig config;
    private final KickOAuthManager kickOAuthManager;
    private final StreamNotificationManager notificationManager;

    @Inject
    public KickService(
        OkHttpClient okHttpClient,
        Gson gson,
        LiveRightNowConfig config,
        KickOAuthManager kickOAuthManager,
        StreamNotificationManager notificationManager
    )
    {
        this.okHttpClient = okHttpClient;
        this.gson = gson;
        this.config = config;
        this.kickOAuthManager = kickOAuthManager;
        this.notificationManager = notificationManager;
    }

    public List<StreamInfo> fetchStreams(List<String> usernames)
    {
        if (usernames == null || usernames.isEmpty())
        {
            return Collections.emptyList();
        }

        String token = config.kickOAuthToken() != null ? config.kickOAuthToken().trim() : "";
        if (token.isEmpty())
        {
            log.warn("Kick OAuth Token is not configured. Skipping Kick lookup.");
            return Collections.emptyList();
        }

        if (token.toLowerCase().startsWith("bearer "))
        {
            token = token.substring(7).trim();
        }

        HttpUrl baseUrl = HttpUrl.parse(KICK_CHANNELS_API_URL);
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
                urlBuilder.addQueryParameter("slug", trimmed);
            }
        }

        Request request = new Request.Builder()
            .url(urlBuilder.build())
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/json")
            .get()
            .build();

        try (Response response = okHttpClient.newCall(request).execute())
        {
            if (response.code() == 401)
            {
                log.warn("Kick API returned 401 Unauthorized (OAuth token expired).");
                kickOAuthManager.handleTokenExpired();
                notificationManager.notifySessionExpired(Platform.KICK);
                return Collections.emptyList();
            }

            if (!response.isSuccessful())
            {
                log.warn("Kick API returned unsuccessful response code: {}", response.code());
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

            Map<String, StreamInfo> streamMap = new HashMap<>();
            JsonArray dataArray = json.getAsJsonArray("data");
            for (JsonElement element : dataArray)
            {
                if (!element.isJsonObject())
                {
                    continue;
                }

                JsonObject channelObj = element.getAsJsonObject();
                String slug = channelObj.has("slug") && !channelObj.get("slug").isJsonNull()
                    ? channelObj.get("slug").getAsString() : "";
                if (slug.isEmpty())
                {
                    continue;
                }

                boolean isLive = false;
                String title = channelObj.has("stream_title") && !channelObj.get("stream_title").isJsonNull()
                    ? channelObj.get("stream_title").getAsString() : "";
                int viewerCount = 0;
                String sessionId = "";

                if (channelObj.has("livestream") && !channelObj.get("livestream").isJsonNull())
                {
                    JsonObject livestream = channelObj.getAsJsonObject("livestream");
                    isLive = !livestream.has("is_live") || livestream.get("is_live").getAsBoolean();
                    if (livestream.has("session_title") && !livestream.get("session_title").isJsonNull())
                    {
                        title = livestream.get("session_title").getAsString();
                    }
                    if (livestream.has("viewer_count") && !livestream.get("viewer_count").isJsonNull())
                    {
                        viewerCount = livestream.get("viewer_count").getAsInt();
                    }
                    if (livestream.has("id") && !livestream.get("id").isJsonNull())
                    {
                        sessionId = livestream.get("id").getAsString();
                    }
                    else if (livestream.has("created_at") && !livestream.get("created_at").isJsonNull())
                    {
                        sessionId = livestream.get("created_at").getAsString();
                    }
                }

                String category = "";
                if (channelObj.has("category") && !channelObj.get("category").isJsonNull() && channelObj.get("category").isJsonObject())
                {
                    JsonObject catObj = channelObj.getAsJsonObject("category");
                    if (catObj.has("name") && !catObj.get("name").isJsonNull())
                    {
                        category = catObj.get("name").getAsString();
                    }
                }

                streamMap.put(slug.toLowerCase(), new StreamInfo(
                    Platform.KICK,
                    slug,
                    isLive,
                    title,
                    category,
                    viewerCount,
                    sessionId
                ));
            }

            List<StreamInfo> results = new ArrayList<>();
            for (String user : usernames)
            {
                String lower = user.trim().toLowerCase();
                if (lower.isEmpty())
                {
                    continue;
                }

                StreamInfo stream = streamMap.get(lower);
                if (stream != null)
                {
                    results.add(new StreamInfo(
                        Platform.KICK,
                        user.trim(),
                        stream.isLive(),
                        stream.getTitle(),
                        stream.getCategory(),
                        stream.getViewerCount(),
                        stream.getSessionId()
                    ));
                }
                else
                {
                    results.add(StreamInfo.offline(Platform.KICK, user.trim()));
                }
            }

            return results;
        }
        catch (IOException | RuntimeException e)
        {
            log.error("Failed to query Kick streams", e);
            return Collections.emptyList();
        }
    }
}
