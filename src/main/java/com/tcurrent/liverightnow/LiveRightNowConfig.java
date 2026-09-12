package com.tcurrent.liverightnow;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(LiveRightNowConfig.GROUP)
public interface LiveRightNowConfig extends Config
{
    String GROUP = "liverightnow";
    String TWITCH_DEFAULT_CLIENT_ID = "bsj8pbpnptei2b3q7zwp70gn14lptn";
    String KICK_DEFAULT_CLIENT_ID = "01M29F2N6HTHHC9E9YPASHHP0T";

    String TWITCH_STREAMERS_KEY = "twitchStreamers";
    String TWITCH_CLIENT_ID_KEY = "twitchClientId";
    String TWITCH_OAUTH_TOKEN_KEY = "twitchOAuthToken";
    String TWITCH_CONNECTED_USER_KEY = "twitchConnectedUser";

    String KICK_STREAMERS_KEY = "kickStreamers";
    String KICK_CLIENT_ID_KEY = "kickClientId";
    String KICK_OAUTH_TOKEN_KEY = "kickOAuthToken";
    String KICK_CONNECTED_USER_KEY = "kickConnectedUser";

    String NOTIFIED_SESSIONS_KEY = "notifiedSessions";

    @ConfigSection(
        name = "Twitch",
        description = "Settings for Twitch streamer alerts",
        position = 0
    )
    String TWITCH_SECTION = "twitchSection";

    @ConfigItem(
        keyName = TWITCH_CONNECTED_USER_KEY,
        name = "Twitch Connected User",
        description = "Connected Twitch username",
        hidden = true
    )
    default String twitchConnectedUser()
    {
        return "";
    }

    @ConfigItem(
        keyName = TWITCH_STREAMERS_KEY,
        name = "Tracked Streamers",
        description = "Comma-separated list of Twitch usernames to track (e.g. b0aty, faux, torvesta)",
        position = 2,
        section = TWITCH_SECTION
    )
    default String twitchStreamers()
    {
        return "";
    }

    @ConfigItem(
        keyName = TWITCH_CLIENT_ID_KEY,
        name = "Twitch Client ID",
        description = "Twitch OAuth Client ID",
        hidden = true
    )
    default String twitchClientId()
    {
        return TWITCH_DEFAULT_CLIENT_ID;
    }

    @ConfigItem(
        keyName = TWITCH_OAUTH_TOKEN_KEY,
        name = "Twitch OAuth Token",
        description = "Twitch OAuth Access Token",
        hidden = true,
        secret = true
    )
    default String twitchOAuthToken()
    {
        return "";
    }

    @ConfigItem(
        keyName = NOTIFIED_SESSIONS_KEY,
        name = "Notified Sessions",
        description = "Persisted live sessions",
        hidden = true,
        secret = true
    )
    default String notifiedSessions()
    {
        return "";
    }

    @ConfigSection(
        name = "Kick",
        description = "Settings for Kick streamer alerts",
        position = 10
    )
    String KICK_SECTION = "kickSection";

    @ConfigItem(
        keyName = KICK_CONNECTED_USER_KEY,
        name = "Kick Connected User",
        description = "Connected Kick username",
        hidden = true
    )
    default String kickConnectedUser()
    {
        return "";
    }

    @ConfigItem(
        keyName = KICK_STREAMERS_KEY,
        name = "Tracked Streamers",
        description = "Comma-separated list of Kick usernames to track (e.g. odablock, adinross)",
        position = 12,
        section = KICK_SECTION
    )
    default String kickStreamers()
    {
        return "";
    }

    @ConfigItem(
        keyName = KICK_CLIENT_ID_KEY,
        name = "Kick Client ID",
        description = "Kick OAuth Client ID",
        hidden = true
    )
    default String kickClientId()
    {
        return KICK_DEFAULT_CLIENT_ID;
    }

    @ConfigItem(
        keyName = KICK_OAUTH_TOKEN_KEY,
        name = "Kick OAuth Token",
        description = "Kick OAuth Access Token",
        hidden = true,
        secret = true
    )
    default String kickOAuthToken()
    {
        return "";
    }

    @ConfigSection(
        name = "Notifications",
        description = "Notification preferences",
        position = 20
    )
    String NOTIFICATION_SECTION = "notificationSection";

    @ConfigItem(
        keyName = "chatMessageEnabled",
        name = "In-game chat message",
        description = "Show the notification in the in-game chat window",
        position = 21,
        section = NOTIFICATION_SECTION
    )
    default boolean chatMessageEnabled()
    {
        return true;
    }

    @ConfigItem(
        keyName = "notificationEnabled",
        name = "RuneLite notifications",
        description = "Show a RuneLite desktop notification when a streamer goes live",
        position = 22,
        section = NOTIFICATION_SECTION
    )
    default boolean notificationEnabled()
    {
        return true;
    }

}
