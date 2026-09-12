package com.tcurrent.liverightnow.notification;

import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.tcurrent.liverightnow.LiveRightNowConfig;
import com.tcurrent.liverightnow.model.Platform;
import com.tcurrent.liverightnow.model.StreamInfo;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;

@Singleton
public class StreamNotificationManager
{
    private final Client client;
    private final ClientThread clientThread;
    private final Notifier notifier;
    private final LiveRightNowConfig config;
    @Inject
    public StreamNotificationManager(
        Client client,
        ClientThread clientThread,
        Notifier notifier,
        LiveRightNowConfig config
    )
    {
        this.client = client;
        this.clientThread = clientThread;
        this.notifier = notifier;
        this.config = config;
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
        sendInGameMessage("[Live Right Now] Your " + platform.getDisplayName() + " session has expired. Please reconnect in the side panel.");
    }

    public void notifyConnectReminder(String messageText)
    {
        sendInGameMessage("[Live Right Now] " + messageText);
    }

    private void sendInGameChat(StreamInfo stream, String platformName, String channel, String title)
    {
        sendInGameMessage("[Live Right Now] " + channel + " is now live on " + platformName);
    }

    private void sendInGameChat(List<StreamInfo> streams)
    {
        StringBuilder message = new StringBuilder("[Live Right Now] ")
            .append(streams.size()).append(" tracked streamers are now live: ");
        int displayed = Math.min(3, streams.size());
        for (int i = 0; i < displayed; i++)
        {
            if (i > 0)
            {
                message.append(", ");
            }
            message.append(streams.get(i).getChannelName());
        }

        int remaining = streams.size() - displayed;
        if (remaining > 0)
        {
            message.append(" and ").append(remaining).append(" more");
        }

        sendInGameMessage(message.toString());
    }

    private void sendInGameMessage(String message)
    {
        String prefix = "[Live Right Now] ";
        String body = message.startsWith(prefix) ? message.substring(prefix.length()) : message;
        String formattedMessage = "<col=00B4D8>" + prefix + "</col>" + body;
        clientThread.invokeLater(() -> client.addChatMessage(
            ChatMessageType.GAMEMESSAGE,
            "[Live Right Now]",
            formattedMessage,
            null,
            false
        ));
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
        notifier.notify(channel + " is now live on " + platformName);
    }
}
