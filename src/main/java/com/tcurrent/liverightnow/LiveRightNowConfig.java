package com.tcurrent.liverightnow;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(LiveRightNowConfig.GROUP)
public interface LiveRightNowConfig extends Config
{
    String GROUP = "liverightnow";
    String TWITCH_DEFAULT_CLIENT_ID = "ka3clhecbpbyijmalu7th9rkfuol9f";

    String TWITCH_STREAMERS_KEY = "twitchStreamers";
    String TWITCH_CLIENT_ID_KEY = "twitchClientId";
    String TWITCH_OAUTH_TOKEN_KEY = "twitchOAuthToken";
    String TWITCH_CONNECTED_USER_KEY = "twitchConnectedUser";
    String NOTIFIED_SESSIONS_KEY = "notifiedSessions";

    String KICK_STREAMERS_KEY = "kickStreamers";

    @ConfigSection(
        name = "Twitch",
        description = "Settings for Twitch streamer alerts",
        position = 0
    )
    String TWITCH_SECTION = "twitchSection";

    @ConfigItem(
        keyName = TWITCH_CONNECTED_USER_KEY,
        name = "",
        description = "",
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
        name = "",
        description = "",
        hidden = true
    )
    default String twitchClientId()
    {
        return TWITCH_DEFAULT_CLIENT_ID;
    }

    @ConfigItem(
        keyName = TWITCH_OAUTH_TOKEN_KEY,
        name = "",
        description = "",
        hidden = true,
        secret = true
    )
    default String twitchOAuthToken()
    {
        return "";
    }

    @ConfigItem(
        keyName = NOTIFIED_SESSIONS_KEY,
        name = "",
        description = "",
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
        keyName = KICK_STREAMERS_KEY,
        name = "Tracked Streamers",
        description = "Comma-separated list of Kick usernames to track",
        position = 12,
        section = KICK_SECTION
    )
    default String kickStreamers()
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
