package com.tcurrent.liverightnow.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import com.tcurrent.liverightnow.model.Platform;
import com.tcurrent.liverightnow.model.StreamInfo;
import com.tcurrent.liverightnow.oauth.KickOAuthManager;
import com.tcurrent.liverightnow.oauth.TwitchOAuthManager;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

@Singleton
public class LiveRightNowPanel extends PluginPanel
{
    private static final Color TWITCH_PURPLE = new Color(145, 70, 255);
    private static final Color KICK_GREEN = new Color(83, 252, 24);
    private static final Color STATUS_ONLINE_COLOR = new Color(76, 175, 80);
    private static final Color STATUS_OFFLINE_COLOR = new Color(150, 150, 150);
    private static final Color DISCONNECT_RED = new Color(220, 53, 69);

    private final TwitchOAuthManager twitchOAuthManager;
    private final KickOAuthManager kickOAuthManager;

    private final JPanel contentPanel = new JPanel();
    private final JPanel accountsPanel = new JPanel();
    private final JPanel streamsPanel = new JPanel();

    @Inject
    public LiveRightNowPanel(TwitchOAuthManager twitchOAuthManager, KickOAuthManager kickOAuthManager)
    {
        super(false);
        this.twitchOAuthManager = twitchOAuthManager;
        this.kickOAuthManager = kickOAuthManager;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        contentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Header Title
        JLabel titleLabel = new JLabel("Live Right Now", SwingConstants.CENTER);
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setAlignmentX(CENTER_ALIGNMENT);
        titleLabel.setBorder(new EmptyBorder(0, 0, 12, 0));
        contentPanel.add(titleLabel);

        // Accounts Container
        accountsPanel.setLayout(new BoxLayout(accountsPanel, BoxLayout.Y_AXIS));
        accountsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        contentPanel.add(accountsPanel);

        contentPanel.add(Box.createRigidArea(new Dimension(0, 15)));

        // Streams Container
        JLabel streamsHeader = new JLabel("Live Streams");
        streamsHeader.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        streamsHeader.setForeground(ColorScheme.BRAND_ORANGE);
        streamsHeader.setBorder(new EmptyBorder(0, 0, 6, 0));
        contentPanel.add(streamsHeader);

        streamsPanel.setLayout(new BoxLayout(streamsPanel, BoxLayout.Y_AXIS));
        streamsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        contentPanel.add(streamsPanel);

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(null);
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        add(scrollPane, BorderLayout.CENTER);

        refreshAccountsUi();
        updateStreams(null);
    }

    public final void refreshAccountsUi()
    {
        SwingUtilities.invokeLater(() -> {
            accountsPanel.removeAll();

            // Twitch Section
            accountsPanel.add(createAccountCard(
                "Twitch",
                TWITCH_PURPLE,
                twitchOAuthManager.isConnected(),
                twitchOAuthManager.getConnectedUser(),
                () -> twitchOAuthManager.startConnectFlow().thenAccept(ok -> refreshAccountsUi()),
                () -> {
                    twitchOAuthManager.disconnect();
                    refreshAccountsUi();
                }
            ));

            accountsPanel.add(Box.createRigidArea(new Dimension(0, 8)));

            // Kick Section
            accountsPanel.add(createAccountCard(
                "Kick",
                KICK_GREEN,
                kickOAuthManager.isConnected(),
                kickOAuthManager.getConnectedUser(),
                () -> kickOAuthManager.startConnectFlow().thenAccept(ok -> refreshAccountsUi()),
                () -> {
                    kickOAuthManager.disconnect();
                    refreshAccountsUi();
                }
            ));

            accountsPanel.revalidate();
            accountsPanel.repaint();
        });
    }

    public final void updateStreams(List<StreamInfo> streams)
    {
        SwingUtilities.invokeLater(() -> {
            streamsPanel.removeAll();

            boolean anyConnected = twitchOAuthManager.isConnected() || kickOAuthManager.isConnected();
            if (!anyConnected)
            {
                JLabel warningLabel = new JLabel("<html><center style='color:#aaa;'>Connect your Twitch or Kick account above to start receiving alerts.</center></html>");
                warningLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
                warningLabel.setBorder(new EmptyBorder(10, 5, 10, 5));
                streamsPanel.add(warningLabel);
            }
            else if (streams == null || streams.isEmpty())
            {
                JLabel emptyLabel = new JLabel("<html><center style='color:#888;'>No tracked streamers online right now.</center></html>");
                emptyLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
                emptyLabel.setBorder(new EmptyBorder(10, 5, 10, 5));
                streamsPanel.add(emptyLabel);
            }
            else
            {
                for (StreamInfo stream : streams)
                {
                    streamsPanel.add(createStreamCard(stream));
                    streamsPanel.add(Box.createRigidArea(new Dimension(0, 6)));
                }
            }

            streamsPanel.revalidate();
            streamsPanel.repaint();
        });
    }

    private JPanel createAccountCard(String platformName, Color brandColor, boolean isConnected, String username, Runnable onConnect, Runnable onDisconnect)
    {
        JPanel card = new JPanel(new BorderLayout(5, 5));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 3, 0, 0, brandColor),
            new EmptyBorder(8, 10, 8, 10)
        ));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JLabel platformLabel = new JLabel(platformName);
        platformLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        platformLabel.setForeground(Color.WHITE);
        topRow.add(platformLabel, BorderLayout.WEST);

        JLabel statusBadge = new JLabel(isConnected ? "● Connected" : "○ Disconnected");
        statusBadge.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        statusBadge.setForeground(isConnected ? STATUS_ONLINE_COLOR : STATUS_OFFLINE_COLOR);
        topRow.add(statusBadge, BorderLayout.EAST);

        card.add(topRow, BorderLayout.NORTH);

        if (isConnected && username != null && !username.trim().isEmpty())
        {
            JLabel userLabel = new JLabel("User: " + username);
            userLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            userLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            card.add(userLabel, BorderLayout.CENTER);
        }

        JPanel btnPanel = new JPanel(new GridLayout(1, isConnected ? 2 : 1, 5, 0));
        btnPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        btnPanel.setBorder(new EmptyBorder(4, 0, 0, 0));

        if (isConnected)
        {
            JButton reconnectBtn = new JButton("Reconnect");
            reconnectBtn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            reconnectBtn.addActionListener(e -> onConnect.run());
            btnPanel.add(reconnectBtn);

            JButton disconnectBtn = new JButton("Disconnect");
            disconnectBtn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            disconnectBtn.setForeground(DISCONNECT_RED);
            disconnectBtn.addActionListener(e -> onDisconnect.run());
            btnPanel.add(disconnectBtn);
        }
        else
        {
            JButton connectBtn = new JButton("Connect " + platformName);
            connectBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            connectBtn.addActionListener(e -> onConnect.run());
            btnPanel.add(connectBtn);
        }

        card.add(btnPanel, BorderLayout.SOUTH);
        return card;
    }

    private JPanel createStreamCard(StreamInfo stream)
    {
        JPanel card = new JPanel(new BorderLayout(4, 4));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 3, 0, 0, stream.getPlatform() == Platform.TWITCH ? TWITCH_PURPLE : KICK_GREEN),
            new EmptyBorder(6, 8, 6, 8)
        ));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                LinkBrowser.open(stream.getStreamUrl());
            }
        });

        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JLabel nameLabel = new JLabel(stream.getChannelName());
        nameLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        nameLabel.setForeground(Color.WHITE);
        headerRow.add(nameLabel, BorderLayout.WEST);

        JLabel liveStatus = new JLabel(stream.isLive() ? "● " + stream.getViewerCount() + " viewers" : "Offline");
        liveStatus.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        liveStatus.setForeground(stream.isLive() ? STATUS_ONLINE_COLOR : STATUS_OFFLINE_COLOR);
        headerRow.add(liveStatus, BorderLayout.EAST);

        card.add(headerRow, BorderLayout.NORTH);

        if (stream.isLive())
        {
            JPanel body = new JPanel(new GridLayout(stream.getCategory().isEmpty() ? 1 : 2, 1));
            body.setBackground(ColorScheme.DARKER_GRAY_COLOR);

            if (!stream.getTitle().isEmpty())
            {
                JLabel titleLabel = new JLabel("<html><body style='width: 150px; text-overflow: ellipsis;'>" + escapeHtml(stream.getTitle()) + "</body></html>");
                titleLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
                titleLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
                body.add(titleLabel);
            }

            if (!stream.getCategory().isEmpty())
            {
                JLabel catLabel = new JLabel(stream.getCategory());
                catLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 10));
                catLabel.setForeground(new Color(170, 170, 170));
                body.add(catLabel);
            }

            card.add(body, BorderLayout.CENTER);
        }

        return card;
    }

    private String escapeHtml(String s)
    {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
