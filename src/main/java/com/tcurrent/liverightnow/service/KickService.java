package com.tcurrent.liverightnow.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tcurrent.liverightnow.model.Platform;
import com.tcurrent.liverightnow.model.StreamInfo;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Singleton
public class KickService
{
    private static final Logger log = LoggerFactory.getLogger(KickService.class);
    private static final String KICK_CHANNEL_API_URL = "https://kick.com/api/v2/channels/";

    private final OkHttpClient okHttpClient;
    private final Gson gson;

    @Inject
    public KickService(
        OkHttpClient okHttpClient,
        Gson gson
    )
    {
        this.okHttpClient = okHttpClient;
        this.gson = gson;
    }

    public List<StreamInfo> fetchStreams(List<String> usernames)
    {
        if (usernames == null || usernames.isEmpty())
        {
            return Collections.emptyList();
        }

        List<StreamInfo> results = new ArrayList<>();
        for (String user : usernames)
        {
            String trimmed = user.trim();
            if (trimmed.isEmpty())
            {
                continue;
            }

            results.add(fetchSingleChannel(trimmed));
        }

        return results;
    }

    private StreamInfo fetchSingleChannel(String username)
    {
        String normalizedUser = username.trim().toLowerCase();
        Request.Builder requestBuilder = new Request.Builder()
            .url(KICK_CHANNEL_API_URL + normalizedUser)
            .header("Accept", "application/json")
            .header("User-Agent", "RuneLite-LiveRightNowPlugin")
            .get();

        Request request = requestBuilder.build();

        try (Response response = okHttpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                return StreamInfo.offline(Platform.KICK, username);
            }

            ResponseBody body = response.body();
            if (body == null)
            {
                return StreamInfo.offline(Platform.KICK, username);
            }

            JsonObject json = gson.fromJson(body.charStream(), JsonObject.class);
            if (json == null)
            {
                return StreamInfo.offline(Platform.KICK, username);
            }

            if (json.has("livestream") && !json.get("livestream").isJsonNull())
            {
                JsonObject livestream = json.getAsJsonObject("livestream");
                String title = livestream.has("session_title") ? livestream.get("session_title").getAsString() : "";
                int viewerCount = livestream.has("viewer_count") ? livestream.get("viewer_count").getAsInt() : 0;
                String sessionId = livestream.has("id") ? livestream.get("id").getAsString() : "";
                if (sessionId.isEmpty() && livestream.has("created_at"))
                {
                    sessionId = livestream.get("created_at").getAsString();
                }
                
                String category = "";
                if (livestream.has("categories") && livestream.get("categories").isJsonArray())
                {
                    JsonArray categories = livestream.getAsJsonArray("categories");
                    if (categories.size() > 0 && categories.get(0).isJsonObject())
                    {
                        JsonObject catObj = categories.get(0).getAsJsonObject();
                        if (catObj.has("name"))
                        {
                            category = catObj.get("name").getAsString();
                        }
                    }
                }

                return new StreamInfo(Platform.KICK, username, true, title, category, viewerCount, sessionId);
            }

            return StreamInfo.offline(Platform.KICK, username);
        }
        catch (IOException | RuntimeException e)
        {
            log.warn("Failed to query Kick channel: {}", username, e);
            return StreamInfo.offline(Platform.KICK, username);
        }
    }
}
