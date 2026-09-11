package com.tcurrent.liverightnow.ui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.tcurrent.liverightnow.LiveRightNowConfig;
import com.tcurrent.liverightnow.model.Platform;
import com.tcurrent.liverightnow.model.StreamInfo;

import net.runelite.api.Client;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

@Singleton
public class BannerNotificationOverlay extends Overlay
{
    private static final int BANNER_WIDTH = 340;
    private static final int BANNER_HEIGHT = 58;
    private static final int TOP_OFFSET = 30;

    private static final Color BORDER_GOLD_OUTER = new Color(218, 165, 32);
    private static final Color BORDER_GOLD_INNER = new Color(133, 94, 20);
    private static final Color BG_TOP = new Color(28, 22, 16, 245);
    private static final Color BG_BOTTOM = new Color(14, 11, 8, 250);

    private static final Color TWITCH_ACCENT = new Color(145, 70, 255);
    private static final Color KICK_ACCENT = new Color(83, 252, 24);
    private static final Color GOLD_TEXT = new Color(255, 215, 0);

    private final Client client;
    private final LiveRightNowConfig config;
    private final ConcurrentLinkedQueue<BannerNotification> bannerQueue = new ConcurrentLinkedQueue<>();

    private BannerNotification currentBanner;
    private Instant bannerStartTime;

    @Inject
    public BannerNotificationOverlay(Client client, LiveRightNowConfig config)
    {
        this.client = client;
        this.config = config;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(PRIORITY_HIGH);
    }

    public void showBanner(StreamInfo stream)
    {
        showBanners(List.of(stream));
    }

    public void showBanners(List<StreamInfo> streams)
    {
        if (!streams.isEmpty())
        {
            bannerQueue.add(new BannerNotification(streams));
        }
    }

    public void clearBanners()
    {
        bannerQueue.clear();
        currentBanner = null;
        bannerStartTime = null;
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.bannerNotificationEnabled())
        {
            return null;
        }

        Instant now = Instant.now();
        int durationSec = Math.max(2, config.bannerDurationSeconds());
        Duration totalDuration = Duration.ofSeconds(durationSec);

        if (currentBanner == null || (bannerStartTime != null && Duration.between(bannerStartTime, now).compareTo(totalDuration) >= 0))
        {
            currentBanner = bannerQueue.poll();
            if (currentBanner == null)
            {
                bannerStartTime = null;
                return null;
            }
            bannerStartTime = now;
        }

        double elapsedMillis = Duration.between(bannerStartTime, now).toMillis();
        double totalMillis = totalDuration.toMillis();
        float opacity = 1.0f;

        // Smooth fade-in (first 400ms) and fade-out (last 600ms)
        if (elapsedMillis < 400)
        {
            opacity = (float) (elapsedMillis / 400.0);
        }
        else if (elapsedMillis > (totalMillis - 600))
        {
            opacity = (float) Math.max(0.0, (totalMillis - elapsedMillis) / 600.0);
        }

        opacity = Math.max(0.0f, Math.min(1.0f, opacity));

        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));

        Rectangle bounds = client.getCanvas().getBounds();
        int x = (bounds.width - BANNER_WIDTH) / 2;
        int y = TOP_OFFSET;

        drawCombatAchievementStyleBanner(g, x, y, currentBanner.streams);

        g.dispose();
        return null;
    }

    private void drawCombatAchievementStyleBanner(Graphics2D g, int x, int y, List<StreamInfo> streams)
    {
        // Background Gradient
        GradientPaint bgGradient = new GradientPaint(x, y, BG_TOP, x, y + BANNER_HEIGHT, BG_BOTTOM);
        g.setPaint(bgGradient);
        g.fillRoundRect(x, y, BANNER_WIDTH, BANNER_HEIGHT, 8, 8);

        // Outer & Inner Gold/Metallic Borders
        g.setColor(BORDER_GOLD_OUTER);
        g.setStroke(new BasicStroke(1.8f));
        g.drawRoundRect(x, y, BANNER_WIDTH, BANNER_HEIGHT, 8, 8);

        g.setColor(BORDER_GOLD_INNER);
        g.setStroke(new BasicStroke(1.0f));
        g.drawRoundRect(x + 2, y + 2, BANNER_WIDTH - 4, BANNER_HEIGHT - 4, 6, 6);

        // Platform Indicator Pill / Badge
        StreamInfo stream = streams.get(0);
        Platform platform = stream.getPlatform();
        Color platformAccent = platform == Platform.TWITCH ? TWITCH_ACCENT : KICK_ACCENT;
        if (streams.stream().map(StreamInfo::getPlatform).distinct().count() > 1)
        {
            platformAccent = new Color(0, 180, 216);
        }

        g.setColor(platformAccent);
        g.fillRoundRect(x + 10, y + 10, 8, BANNER_HEIGHT - 20, 4, 4);

        // Header Title: [STREAMER] IS NOW LIVE!
        Font headerFont = FontManager.getRunescapeBoldFont();
        g.setFont(headerFont);
        FontMetrics headerMetrics = g.getFontMetrics();

        String headerText = streams.size() == 1
            ? stream.getChannelName().toUpperCase() + " IS NOW LIVE!"
            : streams.size() + " STREAMERS ARE LIVE!";
        g.setColor(Color.BLACK);
        g.drawString(headerText, x + 27, y + 21 + 1); // Drop shadow
        g.setColor(GOLD_TEXT);
        g.drawString(headerText, x + 26, y + 21);

        // Platform subtext badge
        String platformTag = streams.size() == 1
            ? " • " + platform.getDisplayName()
            : " • Live Right Now";
        int headerWidth = headerMetrics.stringWidth(headerText);
        g.setColor(platformAccent);
        g.setFont(FontManager.getRunescapeSmallFont());
        g.drawString(platformTag, x + 26 + headerWidth + 2, y + 21);

        // Subtitle / Stream Details
        g.setFont(FontManager.getRunescapeFont());
        g.setColor(Color.BLACK);

        String subtitle = streams.size() == 1
            ? stream.getTitle()
            : formatStreamerNames(streams);
        if (subtitle == null || subtitle.trim().isEmpty())
        {
            subtitle = !stream.getCategory().isEmpty() ? stream.getCategory() : "Click to watch on " + platform.getDisplayName();
        }

        // Truncate if long
        FontMetrics subMetrics = g.getFontMetrics();
        int maxTextWidth = BANNER_WIDTH - 45;
        if (subMetrics.stringWidth(subtitle) > maxTextWidth)
        {
            while (subtitle.length() > 3 && subMetrics.stringWidth(subtitle + "...") > maxTextWidth)
            {
                subtitle = subtitle.substring(0, subtitle.length() - 1);
            }
            subtitle += "...";
        }

        g.drawString(subtitle, x + 27, y + 42 + 1); // Drop shadow
        g.setColor(new Color(225, 225, 225));
        g.drawString(subtitle, x + 26, y + 42);
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

    private static class BannerNotification
    {
        final List<StreamInfo> streams;

        BannerNotification(List<StreamInfo> streams)
        {
            this.streams = streams;
        }
    }
}
