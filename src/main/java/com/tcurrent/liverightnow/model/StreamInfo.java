package com.tcurrent.liverightnow.model;

import java.util.Objects;

public class StreamInfo
{
    private final Platform platform;
    private final String channelName;
    private final boolean live;
    private final String title;
    private final String category;
    private final int viewerCount;
    private final String sessionId;

    public StreamInfo(Platform platform, String channelName, boolean live, String title, String category, int viewerCount)
    {
        this(platform, channelName, live, title, category, viewerCount, "");
    }

    public StreamInfo(
        Platform platform,
        String channelName,
        boolean live,
        String title,
        String category,
        int viewerCount,
        String sessionId
    )
    {
        this.platform = platform;
        this.channelName = channelName;
        this.live = live;
        this.title = title != null ? title : "";
        this.category = category != null ? category : "";
        this.viewerCount = viewerCount;
        this.sessionId = sessionId != null ? sessionId : "";
    }

    public static StreamInfo offline(Platform platform, String channelName)
    {
        return new StreamInfo(platform, channelName, false, "", "", 0);
    }

    public Platform getPlatform()
    {
        return platform;
    }

    public String getChannelName()
    {
        return channelName;
    }

    public boolean isLive()
    {
        return live;
    }

    public String getTitle()
    {
        return title;
    }

    public String getCategory()
    {
        return category;
    }

    public int getViewerCount()
    {
        return viewerCount;
    }

    public String getSessionId()
    {
        return sessionId;
    }

    public String getStreamUrl()
    {
        return platform.getChannelUrl(channelName);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StreamInfo that = (StreamInfo) o;
        return live == that.live &&
                viewerCount == that.viewerCount &&
                platform == that.platform &&
                Objects.equals(channelName.toLowerCase(), that.channelName.toLowerCase()) &&
                Objects.equals(title, that.title) &&
                Objects.equals(category, that.category);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(platform, channelName.toLowerCase(), live, title, category, viewerCount);
    }
}
