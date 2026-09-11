package com.tcurrent.liverightnow;

import java.awt.Color;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(LiveRightNowConfig.GROUP)
public interface LiveRightNowConfig extends Config
{
    String GROUP = "liverightnow";

    String TWITCH_STREAMERS_KEY = "twitchStreamers";
    String TWITCH_CLIENT_ID_KEY = "twitchClientId";
    String TWITCH_OAUTH_TOKEN_KEY = "twitchOAuthToken";
    String TWITCH_CONNECTED_USER_KEY = "twitchConnectedUser";

    String KICK_STREAMERS_KEY = "kickStreamers";
    String KICK_OAUTH_TOKEN_KEY = "kickOAuthToken";
    String KICK_CONNECTED_USER_KEY = "kickConnectedUser";

    @ConfigSection(
        name = "Twitch",
        description = "Settings for Twitch streamer alerts",
        position = 0
    )
    String TWITCH_SECTION = "twitchSection";

    @ConfigItem(
        keyName = TWITCH_CONNECTED_USER_KEY,
        name = "Connected Account",
        description = "The Twitch account currently connected via OAuth",
        position = 1,
        section = TWITCH_SECTION
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
        return "kimne78kx3ncx6brgo4mv6wki5h1ko";
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

    @ConfigSection(
        name = "Kick",
        description = "Settings for Kick streamer alerts",
        position = 10
    )
    String KICK_SECTION = "kickSection";

    @ConfigItem(
        keyName = KICK_CONNECTED_USER_KEY,
        name = "Connected Account",
        description = "The Kick account currently connected via OAuth",
        position = 11,
        section = KICK_SECTION
    )
    default String kickConnectedUser()
    {
        return "";
    }

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

    @ConfigItem(
        keyName = KICK_OAUTH_TOKEN_KEY,
        name = "",
        description = "",
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
        keyName = "bannerNotificationEnabled",
        name = "In-game popup banner",
        description = "Display a Combat Achievement style banner at the top of the screen when a streamer goes live",
        position = 21,
        section = NOTIFICATION_SECTION
    )
    default boolean bannerNotificationEnabled()
    {
        return true;
    }

    @Range(min = 2, max = 15)
    @ConfigItem(
        keyName = "bannerDurationSeconds",
        name = "Banner duration (sec)",
        description = "How long the in-game popup banner remains on screen",
        position = 22,
        section = NOTIFICATION_SECTION
    )
    default int bannerDurationSeconds()
    {
        return 5;
    }

    @ConfigItem(
        keyName = "chatMessageEnabled",
        name = "In-game chat message",
        description = "Show the notification in the in-game chat window",
        position = 23,
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
        position = 24,
        section = NOTIFICATION_SECTION
    )
    default boolean notificationEnabled()
    {
        return false;
    }

    @ConfigItem(
        keyName = "messageColor",
        name = "Message color",
        description = "Color for the stream alert message text in the in-game chat window",
        position = 25,
        section = NOTIFICATION_SECTION
    )
    default Color messageColor()
    {
        return Color.WHITE;
    }
}
