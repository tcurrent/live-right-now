package com.tcurrent.liverightnow.model;

public enum Platform
{
    TWITCH("Twitch", "https://twitch.tv/"),
    KICK("Kick", "https://kick.com/");

    private final String displayName;
    private final String baseUrl;

    Platform(String displayName, String baseUrl)
    {
        this.displayName = displayName;
        this.baseUrl = baseUrl;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public String getBaseUrl()
    {
        return baseUrl;
    }

    public String getChannelUrl(String username)
    {
        return baseUrl + username;
    }
}
