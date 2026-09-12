package com.tcurrent.liverightnow.notification;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

        List<MergedStream> merged = mergeByChannelName(streams);

        if (merged.size() == 1)
        {
            notifySingleStreamerLive(merged.get(0));
            return;
        }

        if (config.chatMessageEnabled())
        {
            sendInGameChat(merged);
        }

        if (config.notificationEnabled())
        {
            String names = formatStreamerNames(merged);
            notifier.notify(merged.size() + " tracked streamers are now live: " + names);
        }
    }

    // Combines entries for the same streamer so being live on Twitch and Kick at once
    // produces a single "X is now live on Twitch and Kick" message instead of two.
    private List<MergedStream> mergeByChannelName(List<StreamInfo> streams)
    {
        Map<String, MergedStream> merged = new LinkedHashMap<>();
        for (StreamInfo stream : streams)
        {
            String key = stream.getChannelName().toLowerCase();
            MergedStream existing = merged.get(key);
            if (existing == null)
            {
                merged.put(key, new MergedStream(stream.getChannelName(), stream.getPlatform().getDisplayName(), stream.getTitle()));
            }
            else
            {
                existing.platformNames.add(stream.getPlatform().getDisplayName());
            }
        }
        return new ArrayList<>(merged.values());
    }

    private void notifySingleStreamerLive(MergedStream stream)
    {
        String platformLabel = stream.platformLabel();

        if (config.chatMessageEnabled())
        {
            sendInGameMessage("[Live Right Now] " + stream.channelName + " is now live on " + platformLabel);
        }

        if (config.notificationEnabled())
        {
            notifier.notify(stream.channelName + " is now live on " + platformLabel);
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

    private void sendInGameChat(List<MergedStream> streams)
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
            message.append(streams.get(i).displayName());
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

    private String formatStreamerNames(List<MergedStream> streams)
    {
        String names = streams.stream()
            .limit(3)
            .map(MergedStream::displayName)
            .collect(Collectors.joining(", "));

        int remaining = streams.size() - 3;
        if (remaining > 0)
        {
            names += " and " + remaining + " more";
        }
        return names;
    }

    private static final class MergedStream
    {
        final String channelName;
        final List<String> platformNames;
        final String title;

        MergedStream(String channelName, String platformName, String title)
        {
            this.channelName = channelName;
            this.platformNames = new ArrayList<>();
            this.platformNames.add(platformName);
            this.title = title;
        }

        String platformLabel()
        {
            return String.join(" and ", platformNames);
        }

        String displayName()
        {
            return platformNames.size() > 1 ? channelName + " (" + platformLabel() + ")" : channelName;
        }
    }
}
