package com.tcurrent.liverightnow.notification;

import java.awt.Color;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.tcurrent.liverightnow.LiveRightNowConfig;
import com.tcurrent.liverightnow.model.Platform;
import com.tcurrent.liverightnow.model.StreamInfo;
import com.tcurrent.liverightnow.ui.BannerNotificationOverlay;

import net.runelite.api.ChatMessageType;
import net.runelite.client.Notifier;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.util.ColorUtil;

@Singleton
public class StreamNotificationManager
{
    private static final Color PLATFORM_TWITCH_COLOR = new Color(145, 70, 255);
    private static final Color PLATFORM_KICK_COLOR = new Color(83, 252, 24);

    private final ChatMessageManager chatMessageManager;
    private final Notifier notifier;
    private final LiveRightNowConfig config;
    private final BannerNotificationOverlay bannerOverlay;

    @Inject
    public StreamNotificationManager(
        ChatMessageManager chatMessageManager,
        Notifier notifier,
        LiveRightNowConfig config,
        BannerNotificationOverlay bannerOverlay
    )
    {
        this.chatMessageManager = chatMessageManager;
        this.notifier = notifier;
        this.config = config;
        this.bannerOverlay = bannerOverlay;
    }

    public void notifyStreamerLive(StreamInfo stream)
    {
        notifyStreamersLive(List.of(stream));
    }

    public void notifyStreamersLive(List<StreamInfo> streams)
    {
        if (streams.isEmpty())
        {
            return;
        }

        if (streams.size() == 1)
        {
            notifySingleStreamerLive(streams.get(0));
            return;
        }

        if (config.bannerNotificationEnabled())
        {
            bannerOverlay.showBanners(streams);
        }

        if (config.chatMessageEnabled())
        {
            sendInGameChat(streams);
        }

        if (config.notificationEnabled())
        {
            String names = formatStreamerNames(streams);
            notifier.notify(streams.size() + " tracked streamers are now live: " + names);
        }
    }

    private void notifySingleStreamerLive(StreamInfo stream)
    {
        String platformName = stream.getPlatform().getDisplayName();
        String channel = stream.getChannelName();
        String title = stream.getTitle();

        if (config.bannerNotificationEnabled())
        {
            bannerOverlay.showBanner(stream);
        }

        if (config.chatMessageEnabled())
        {
            sendInGameChat(stream, platformName, channel, title);
        }

        if (config.notificationEnabled())
        {
            sendDesktopNotification(platformName, channel, title);
        }
    }

    public void notifySessionExpired(Platform platform)
    {
        Color platformColor = platform == Platform.TWITCH
            ? PLATFORM_TWITCH_COLOR
            : PLATFORM_KICK_COLOR;

        Color messageColor = config.messageColor();

        ChatMessageBuilder message = new ChatMessageBuilder()
            .append(platformColor, "[Live Right Now] ")
            .append(messageColor, "Your " + platform.getDisplayName() + " session has expired. Please reconnect in the side panel.");

        chatMessageManager.queue(QueuedMessage.builder()
            .type(ChatMessageType.CONSOLE)
            .runeLiteFormattedMessage(message.build())
            .build());
    }

    public void notifyConnectReminder(String messageText)
    {
        Color prefixColor = new Color(0, 180, 216);
        Color messageColor = config.messageColor();

        ChatMessageBuilder message = new ChatMessageBuilder()
            .append(prefixColor, "[Live Right Now] ")
            .append(messageColor, messageText);

        chatMessageManager.queue(QueuedMessage.builder()
            .type(ChatMessageType.CONSOLE)
            .runeLiteFormattedMessage(message.build())
            .build());
    }

    private void sendInGameChat(StreamInfo stream, String platformName, String channel, String title)
    {
        Color platformColor = stream.getPlatform() == Platform.TWITCH
            ? PLATFORM_TWITCH_COLOR
            : PLATFORM_KICK_COLOR;

        Color messageColor = config.messageColor();

        ChatMessageBuilder message = new ChatMessageBuilder()
            .append(platformColor, "[Live Right Now] ")
            .append(ColorUtil.wrapWithColorTag(channel, platformColor))
            .append(messageColor, " is now live on " + platformName);

        if (title != null && !title.trim().isEmpty())
        {
            message.append(messageColor, ": " + title);
        }

        chatMessageManager.queue(QueuedMessage.builder()
            .type(ChatMessageType.CONSOLE)
            .runeLiteFormattedMessage(message.build())
            .build());
    }

    private void sendInGameChat(List<StreamInfo> streams)
    {
        ChatMessageBuilder message = new ChatMessageBuilder()
            .append(new Color(0, 180, 216), "[Live Right Now] ")
            .append(config.messageColor(), streams.size() + " tracked streamers are now live: ");

        int displayed = Math.min(3, streams.size());
        for (int i = 0; i < displayed; i++)
        {
            StreamInfo stream = streams.get(i);
            Color platformColor = stream.getPlatform() == Platform.TWITCH
                ? PLATFORM_TWITCH_COLOR
                : PLATFORM_KICK_COLOR;

            if (i > 0)
            {
                message.append(config.messageColor(), ", ");
            }
            message.append(ColorUtil.wrapWithColorTag(stream.getChannelName(), platformColor));
        }

        int remaining = streams.size() - displayed;
        if (remaining > 0)
        {
            message.append(config.messageColor(), " and " + remaining + " more");
        }

        chatMessageManager.queue(QueuedMessage.builder()
            .type(ChatMessageType.CONSOLE)
            .runeLiteFormattedMessage(message.build())
            .build());
    }

    private String formatStreamerNames(List<StreamInfo> streams)
    {
        String names = streams.stream()
            .limit(3)
            .map(StreamInfo::getChannelName)
            .collect(Collectors.joining(", "));

        int remaining = streams.size() - 3;
        if (remaining > 0)
        {
            names += " and " + remaining + " more";
        }
        return names;
    }

    private void sendDesktopNotification(String platformName, String channel, String title)
    {
        String notificationBody = channel + " is now live on " + platformName;
        if (title != null && !title.trim().isEmpty())
        {
            notificationBody += ": " + title;
        }

        notifier.notify(notificationBody);
    }
}
