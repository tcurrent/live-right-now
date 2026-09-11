package com.tcurrent.liverightnow;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Provides;
import com.tcurrent.liverightnow.model.Platform;
import com.tcurrent.liverightnow.model.StreamInfo;
import com.tcurrent.liverightnow.notification.StreamNotificationManager;
import com.tcurrent.liverightnow.oauth.KickOAuthManager;
import com.tcurrent.liverightnow.oauth.TwitchOAuthManager;
import com.tcurrent.liverightnow.service.KickService;
import com.tcurrent.liverightnow.service.TwitchService;
import com.tcurrent.liverightnow.ui.BannerNotificationOverlay;
import com.tcurrent.liverightnow.ui.LiveRightNowPanel;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
    name = "Live Right Now",
    description = "Tracks specific Twitch and Kick streamers and alerts when they go live",
    enabledByDefault = false,
    tags = {"live", "activity", "tracker", "twitch", "kick", "stream", "notifications"}
)
public class LiveRightNowPlugin extends Plugin
{
    private static final Logger log = LoggerFactory.getLogger(LiveRightNowPlugin.class);

    @Inject
    private Client client;

    @Inject
    private LiveRightNowConfig config;

    @Inject
    private TwitchService twitchService;

    @Inject
    private KickService kickService;

    @Inject
    private TwitchOAuthManager twitchOAuthManager;

    @Inject
    private KickOAuthManager kickOAuthManager;

    @Inject
    private StreamNotificationManager notificationManager;

    @Inject
    private ScheduledExecutorService executorService;

    @Inject
    private ConfigManager configManager;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private LiveRightNowPanel panel;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private BannerNotificationOverlay bannerOverlay;

    private static final int POLLING_INTERVAL_MINUTES = 2;

    private NavigationButton navButton;
    private final Map<String, Boolean> liveStateCache = new ConcurrentHashMap<>();
    private ScheduledFuture<?> pollingTask;
    private boolean initialized = false;
    private boolean loginReminderSent = false;

    private static final String NOTIFIED_SESSION_PREFIX = "notifiedSession.";

    @Provides
    LiveRightNowConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(LiveRightNowConfig.class);
    }

    @Override
    protected void startUp()
    {
        liveStateCache.clear();
        initialized = false;
        loginReminderSent = false;

        overlayManager.add(bannerOverlay);

        BufferedImage icon = createPluginIcon();
        navButton = NavigationButton.builder()
            .tooltip("Live Right Now")
            .icon(icon)
            .priority(7)
            .panel(panel)
            .build();

        clientToolbar.addNavigation(navButton);
        panel.refreshAccountsUi();

        if (client.getGameState() == GameState.LOGGED_IN)
        {
            checkAndSendConnectReminder();
        }

        startPolling();
        log.info("Live Right Now plugin started");
    }

    @Override
    protected void shutDown()
    {
        stopPolling();
        overlayManager.remove(bannerOverlay);
        bannerOverlay.clearBanners();
        clientToolbar.removeNavigation(navButton);
        liveStateCache.clear();
        initialized = false;
        loginReminderSent = false;
        log.info("Live Right Now plugin stopped");
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN)
        {
            checkAndSendConnectReminder();
        }
        else if (event.getGameState() == GameState.LOGIN_SCREEN)
        {
            loginReminderSent = false;
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!LiveRightNowConfig.GROUP.equals(event.getGroup()))
        {
            return;
        }

        if (LiveRightNowConfig.TWITCH_OAUTH_TOKEN_KEY.equals(event.getKey()) ||
            LiveRightNowConfig.KICK_OAUTH_TOKEN_KEY.equals(event.getKey()) ||
            LiveRightNowConfig.TWITCH_CONNECTED_USER_KEY.equals(event.getKey()) ||
            LiveRightNowConfig.KICK_CONNECTED_USER_KEY.equals(event.getKey()))
        {
            panel.refreshAccountsUi();
            executorService.execute(this::checkStreams);
        }
        else if (LiveRightNowConfig.TWITCH_STREAMERS_KEY.equals(event.getKey()) ||
                 LiveRightNowConfig.KICK_STREAMERS_KEY.equals(event.getKey()))
        {
            if (client.getGameState() == GameState.LOGGED_IN)
            {
                checkAndSendConnectReminder();
            }
            executorService.execute(this::checkStreams);
        }
    }

    private synchronized void startPolling()
    {
        if (pollingTask != null && !pollingTask.isCancelled())
        {
            pollingTask.cancel(true);
        }

        pollingTask = executorService.scheduleWithFixedDelay(
            this::checkStreams,
            0,
            POLLING_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        );
    }

    private synchronized void stopPolling()
    {
        if (pollingTask != null)
        {
            pollingTask.cancel(true);
            pollingTask = null;
        }
    }

    private void checkStreams()
    {
        try
        {
            boolean twitchConnected = twitchOAuthManager.isConnected();
            boolean kickConnected = kickOAuthManager.isConnected();

            // Do not perform polling if no accounts are connected
            if (!twitchConnected && !kickConnected)
            {
                log.debug("No accounts connected. Skipping stream polling.");
                panel.updateStreams(Collections.emptyList());
                return;
            }

            List<StreamInfo> activeStreams = new ArrayList<>();

            if (twitchConnected)
            {
                List<String> twitchChannels = parseChannelList(config.twitchStreamers());
                if (!twitchChannels.isEmpty())
                {
                    List<StreamInfo> twitchStreams = twitchService.fetchStreams(twitchChannels);
                    processStreamUpdates(twitchStreams);
                    for (StreamInfo s : twitchStreams)
                    {
                        if (s.isLive())
                        {
                            activeStreams.add(s);
                        }
                    }
                }
            }

            if (kickConnected)
            {
                List<String> kickChannels = parseChannelList(config.kickStreamers());
                if (!kickChannels.isEmpty())
                {
                    List<StreamInfo> kickStreams = kickService.fetchStreams(kickChannels);
                    processStreamUpdates(kickStreams);
                    for (StreamInfo s : kickStreams)
                    {
                        if (s.isLive())
                        {
                            activeStreams.add(s);
                        }
                    }
                }
            }

            panel.updateStreams(activeStreams);

            if (!initialized)
            {
                initialized = true;
            }
        }
        catch (Exception e)
        {
            log.error("Error during stream status polling", e);
        }
    }

    private void processStreamUpdates(List<StreamInfo> streams)
    {
        List<StreamInfo> newSessions = new ArrayList<>();
        for (StreamInfo stream : streams)
        {
            String key = makeCacheKey(stream.getPlatform(), stream.getChannelName());
            boolean wasLive = liveStateCache.getOrDefault(key, false);
            boolean isLiveNow = stream.isLive();

            if (isLiveNow)
            {
                String sessionId = stream.getSessionId().trim();
                boolean newSession;
                if (!sessionId.isEmpty())
                {
                    String notifiedSession = configManager.getConfiguration(
                        LiveRightNowConfig.GROUP,
                        NOTIFIED_SESSION_PREFIX + key
                    );
                    newSession = !sessionId.equals(notifiedSession);
                }
                else
                {
                    newSession = initialized && !wasLive;
                }

                if (newSession)
                {
                    newSessions.add(stream);
                    if (!sessionId.isEmpty())
                    {
                        configManager.setConfiguration(
                            LiveRightNowConfig.GROUP,
                            NOTIFIED_SESSION_PREFIX + key,
                            sessionId
                        );
                    }
                }
                liveStateCache.put(key, true);
            }
            else if (!isLiveNow && wasLive)
            {
                liveStateCache.put(key, false);
            }
            else if (!liveStateCache.containsKey(key))
            {
                liveStateCache.put(key, isLiveNow);
            }
        }

        notificationManager.notifyStreamersLive(newSessions);
    }

    private String makeCacheKey(Platform platform, String channelName)
    {
        return platform.name() + ":" + channelName.toLowerCase();
    }

    static List<String> parseChannelList(String raw)
    {
        if (raw == null || raw.trim().isEmpty())
        {
            return Collections.emptyList();
        }

        Map<String, String> seen = new java.util.LinkedHashMap<>();
        for (String item : raw.split("[,\\n\\r]+"))
        {
            String trimmed = item.trim();
            if (!trimmed.isEmpty())
            {
                seen.putIfAbsent(trimmed.toLowerCase(), trimmed);
            }
        }

        return new ArrayList<>(seen.values());
    }

    private void checkAndSendConnectReminder()
    {
        boolean hasTwitchStreamers = !parseChannelList(config.twitchStreamers()).isEmpty();
        boolean hasKickStreamers = !parseChannelList(config.kickStreamers()).isEmpty();
        boolean twitchConnected = twitchOAuthManager.isConnected();
        boolean kickConnected = kickOAuthManager.isConnected();

        boolean needsTwitch = hasTwitchStreamers && !twitchConnected;
        boolean needsKick = hasKickStreamers && !kickConnected;

        if (needsTwitch && needsKick)
        {
            if (!loginReminderSent)
            {
                loginReminderSent = true;
                notificationManager.notifyConnectReminder("Connect your Twitch and Kick accounts in the side panel to get started with stream notifications.");
            }
        }
        else if (needsTwitch)
        {
            if (!loginReminderSent)
            {
                loginReminderSent = true;
                notificationManager.notifyConnectReminder("Connect your Twitch account in the side panel to get started with stream notifications.");
            }
        }
        else if (needsKick)
        {
            if (!loginReminderSent)
            {
                loginReminderSent = true;
                notificationManager.notifyConnectReminder("Connect your Kick account in the side panel to get started with stream notifications.");
            }
        }
    }

    private BufferedImage createPluginIcon()
    {
        int size = 16;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Top-left Twitch half
        java.awt.Polygon twitchPoly = new java.awt.Polygon(new int[]{0, size, 0}, new int[]{0, 0, size}, 3);
        g.setClip(twitchPoly);
        g.setColor(new Color(145, 70, 255));
        g.fillRect(0, 0, size, size);

        // Bottom-right Kick half
        java.awt.Polygon kickPoly = new java.awt.Polygon(new int[]{size, size, 0}, new int[]{0, size, size}, 3);
        g.setClip(kickPoly);
        g.setColor(new Color(18, 18, 18));
        g.fillRect(0, 0, size, size);

        // Kick Green K letter mark
        g.setColor(new Color(83, 252, 24));
        g.fillRect(8, 6, 2, 8);

        // Diagonal divider
        g.setClip(null);
        g.setColor(new Color(0, 0, 0, 180));
        g.drawLine(size, 0, 0, size);

        g.dispose();
        return image;
    }
}
