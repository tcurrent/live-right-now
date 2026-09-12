package com.tcurrent.liverightnow.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
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

    // Kick's public channels endpoint keys live status off broadcaster_user_id;
    // slugs are only usable for the initial lookup, so cache the resolved id per username.
    private final Map<String, Long> resolvedBroadcasterIds = new ConcurrentHashMap<>();

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

        List<String> normalizedUsers = new ArrayList<>();
        for (String user : usernames)
        {
            String trimmed = user.trim().toLowerCase();
            if (!trimmed.isEmpty())
            {
                normalizedUsers.add(trimmed);
            }
        }

        if (normalizedUsers.isEmpty())
        {
            return Collections.emptyList();
        }

        List<String> unresolved = new ArrayList<>();
        List<Long> resolvedIds = new ArrayList<>();
        for (String user : normalizedUsers)
        {
            Long id = resolvedBroadcasterIds.get(user);
            if (id != null)
            {
                resolvedIds.add(id);
            }
            else
            {
                unresolved.add(user);
            }
        }

        // Kick's channels endpoint disallows mixing slug and broadcaster_user_id in a single request.
        Map<String, JsonObject> channelsByUsername = new HashMap<>();
        if (!unresolved.isEmpty())
        {
            List<JsonObject> channels = requestChannels("slug", unresolved, token);
            if (channels == null)
            {
                return Collections.emptyList();
            }
            for (JsonObject channelObj : channels)
            {
                String slug = getString(channelObj, "slug");
                if (slug.isEmpty())
                {
                    continue;
                }
                Long id = getLong(channelObj, "broadcaster_user_id");
                if (id != null)
                {
                    resolvedBroadcasterIds.put(slug.toLowerCase(), id);
                }
                channelsByUsername.put(slug.toLowerCase(), channelObj);
            }
        }

        if (!resolvedIds.isEmpty())
        {
            List<String> idStrings = new ArrayList<>();
            for (Long id : resolvedIds)
            {
                idStrings.add(String.valueOf(id));
            }
            List<JsonObject> channels = requestChannels("broadcaster_user_id", idStrings, token);
            if (channels == null)
            {
                return Collections.emptyList();
            }
            for (JsonObject channelObj : channels)
            {
                String slug = getString(channelObj, "slug");
                if (!slug.isEmpty())
                {
                    channelsByUsername.put(slug.toLowerCase(), channelObj);
                }
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

            JsonObject channelObj = channelsByUsername.get(lower);
            results.add(channelObj != null
                ? parseChannel(user.trim(), channelObj)
                : StreamInfo.offline(Platform.KICK, user.trim()));
        }

        return results;
    }

    /**
     * Returns null on a request failure (401/unsuccessful/IO error) so callers can bail out entirely.
     */
    private List<JsonObject> requestChannels(String paramName, List<String> values, String token)
    {
        HttpUrl baseUrl = HttpUrl.parse(KICK_CHANNELS_API_URL);
        if (baseUrl == null)
        {
            return null;
        }

        HttpUrl.Builder urlBuilder = baseUrl.newBuilder();
        for (String value : values)
        {
            urlBuilder.addQueryParameter(paramName, value);
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
                return null;
            }

            if (!response.isSuccessful())
            {
                log.warn("Kick API returned unsuccessful response code: {}", response.code());
                return null;
            }

            ResponseBody body = response.body();
            if (body == null)
            {
                return Collections.emptyList();
            }

            JsonObject json = gson.fromJson(body.charStream(), JsonObject.class);
            if (json == null || !json.has("data") || !json.get("data").isJsonArray())
            {
                return Collections.emptyList();
            }

            List<JsonObject> channels = new ArrayList<>();
            for (JsonElement element : json.getAsJsonArray("data"))
            {
                if (element.isJsonObject())
                {
                    channels.add(element.getAsJsonObject());
                }
            }
            return channels;
        }
        catch (IOException | RuntimeException e)
        {
            log.error("Failed to query Kick channels", e);
            return null;
        }
    }

    private StreamInfo parseChannel(String displayName, JsonObject channelObj)
    {
        String title = getString(channelObj, "stream_title");
        String category = "";
        boolean isLive = false;
        int viewerCount = 0;
        String sessionId = "";

        if (channelObj.has("category") && channelObj.get("category").isJsonObject())
        {
            category = getString(channelObj.getAsJsonObject("category"), "name");
        }

        if (channelObj.has("stream") && channelObj.get("stream").isJsonObject())
        {
            JsonObject stream = channelObj.getAsJsonObject("stream");
            isLive = stream.has("is_live") && !stream.get("is_live").isJsonNull() && stream.get("is_live").getAsBoolean();
            if (stream.has("viewer_count") && !stream.get("viewer_count").isJsonNull())
            {
                viewerCount = stream.get("viewer_count").getAsInt();
            }
            sessionId = getString(stream, "start_time");
        }

        return new StreamInfo(Platform.KICK, displayName, isLive, title, category, viewerCount, sessionId);
    }

    private String getString(JsonObject obj, String key)
    {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : "";
    }

    private Long getLong(JsonObject obj, String key)
    {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsLong() : null;
    }
}
