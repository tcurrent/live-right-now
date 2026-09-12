package com.tcurrent.liverightnow.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
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

import com.tcurrent.liverightnow.LiveRightNowConfig;
import com.tcurrent.liverightnow.model.Platform;
import com.tcurrent.liverightnow.model.StreamInfo;
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
    private final LiveRightNowConfig config;

    private final JPanel contentPanel = new JPanel();
    private final JPanel accountsPanel = new JPanel();
    private final JPanel streamsPanel = new JPanel();

    @Inject
    public LiveRightNowPanel(TwitchOAuthManager twitchOAuthManager, LiveRightNowConfig config)
    {
        super(false);
        this.twitchOAuthManager = twitchOAuthManager;
        this.config = config;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        contentPanel.setLayout(new GridBagLayout());
        contentPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        contentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Header Title
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        titleRow.setAlignmentX(LEFT_ALIGNMENT);

        JLabel titleLabel = new JLabel("Live Right Now", SwingConstants.LEFT);
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setHorizontalAlignment(SwingConstants.LEFT);
        titleRow.add(titleLabel, BorderLayout.WEST);
        titleRow.setBorder(new EmptyBorder(0, 0, 12, 0));
        titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, titleRow.getPreferredSize().height));
        addFullWidth(titleRow, 0, new Insets(0, 0, 12, 0));

        // Accounts Container
        accountsPanel.setLayout(new BoxLayout(accountsPanel, BoxLayout.Y_AXIS));
        accountsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        addFullWidth(accountsPanel, 1, new Insets(0, 0, 0, 0));

        addFullWidth(Box.createRigidArea(new Dimension(0, 15)), 2, new Insets(0, 0, 0, 0));

        // Streams Container
        JPanel streamsHeaderRow = new JPanel(new BorderLayout());
        streamsHeaderRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        streamsHeaderRow.setAlignmentX(LEFT_ALIGNMENT);

        JLabel streamsHeader = new JLabel("Live Streams", SwingConstants.LEFT);
        streamsHeader.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        streamsHeader.setForeground(ColorScheme.BRAND_ORANGE);
        streamsHeader.setHorizontalAlignment(SwingConstants.LEFT);
        streamsHeaderRow.add(streamsHeader, BorderLayout.WEST);
        streamsHeaderRow.setBorder(new EmptyBorder(0, 0, 6, 0));
        streamsHeaderRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, streamsHeaderRow.getPreferredSize().height));
        addFullWidth(streamsHeaderRow, 3, new Insets(0, 0, 6, 0));

        streamsPanel.setLayout(new BoxLayout(streamsPanel, BoxLayout.Y_AXIS));
        streamsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        addFullWidth(streamsPanel, 4, new Insets(0, 0, 0, 0));

        GridBagConstraints bottomFiller = new GridBagConstraints();
        bottomFiller.gridx = 0;
        bottomFiller.gridy = 5;
        bottomFiller.weightx = 1.0;
        bottomFiller.weighty = 1.0;
        bottomFiller.fill = GridBagConstraints.BOTH;
        contentPanel.add(Box.createGlue(), bottomFiller);

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(null);
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        add(scrollPane, BorderLayout.CENTER);

        refreshAccountsUi();
        updateStreams(null);
    }

    private void addFullWidth(java.awt.Component component, int row, Insets insets)
    {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = insets;
        contentPanel.add(component, constraints);
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
            accountsPanel.add(createPublicMonitoringCard("Kick"));

            accountsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, accountsPanel.getPreferredSize().height));
            accountsPanel.revalidate();
            accountsPanel.repaint();
        });
    }

    public final void updateStreams(List<StreamInfo> streams)
    {
        SwingUtilities.invokeLater(() -> {
            streamsPanel.removeAll();

            boolean anyConnected = twitchOAuthManager.isConnected() ||
                (config.kickStreamers() != null && !config.kickStreamers().trim().isEmpty());
            if (!anyConnected)
            {
                JPanel warningRow = new JPanel(new BorderLayout());
                warningRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
                warningRow.setAlignmentX(LEFT_ALIGNMENT);
                JLabel warningLabel = new JLabel("<html><body style='color:#aaa;width:180px;'>Connect an account above to start receiving alerts.</body></html>");
                warningLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
                warningLabel.setHorizontalAlignment(SwingConstants.LEFT);
                warningRow.setBorder(new EmptyBorder(10, 5, 10, 5));
                warningRow.add(warningLabel, BorderLayout.WEST);
                streamsPanel.add(warningRow);
            }
            else if (streams == null || streams.isEmpty())
            {
                JPanel emptyRow = new JPanel(new BorderLayout());
                emptyRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
                emptyRow.setAlignmentX(LEFT_ALIGNMENT);
                JLabel emptyLabel = new JLabel("<html><body style='color:#888;'>No tracked streamers online right now.</body></html>");
                emptyLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
                emptyLabel.setHorizontalAlignment(SwingConstants.LEFT);
                emptyRow.setBorder(new EmptyBorder(10, 5, 10, 5));
                emptyRow.add(emptyLabel, BorderLayout.WEST);
                streamsPanel.add(emptyRow);
            }
            else
            {
                for (StreamInfo stream : streams)
                {
                    streamsPanel.add(createStreamCard(stream));
                    streamsPanel.add(Box.createRigidArea(new Dimension(0, 6)));
                }
            }

            streamsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, streamsPanel.getPreferredSize().height));
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

        JPanel topRow = new JPanel();
        topRow.setLayout(new BoxLayout(topRow, BoxLayout.Y_AXIS));
        topRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JLabel platformLabel = new JLabel(platformName);
        platformLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        platformLabel.setForeground(Color.WHITE);
        platformLabel.setAlignmentX(LEFT_ALIGNMENT);
        topRow.add(platformLabel);

        JLabel statusBadge = new JLabel(isConnected ? "● Connected" : "○ Disconnected");
        statusBadge.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        statusBadge.setForeground(isConnected ? STATUS_ONLINE_COLOR : STATUS_OFFLINE_COLOR);
        statusBadge.setAlignmentX(LEFT_ALIGNMENT);
        topRow.add(statusBadge);

        card.add(topRow, BorderLayout.NORTH);

        JLabel userLabel = new JLabel(isConnected && username != null && !username.trim().isEmpty()
            ? "User: " + username
            : "No account connected");
        userLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        userLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        card.add(userLabel, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel();
        btnPanel.setLayout(new BoxLayout(btnPanel, BoxLayout.Y_AXIS));
        btnPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        btnPanel.setBorder(new EmptyBorder(4, 0, 0, 0));

        if (isConnected)
        {
            JButton reconnectBtn = new JButton("Reconnect");
            reconnectBtn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            reconnectBtn.setHorizontalAlignment(SwingConstants.LEFT);
            reconnectBtn.setAlignmentX(LEFT_ALIGNMENT);
            reconnectBtn.setMaximumSize(reconnectBtn.getPreferredSize());
            reconnectBtn.addActionListener(e -> onConnect.run());
            btnPanel.add(reconnectBtn);

            btnPanel.add(Box.createRigidArea(new Dimension(0, 4)));

            JButton disconnectBtn = new JButton("Disconnect");
            disconnectBtn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            disconnectBtn.setForeground(DISCONNECT_RED);
            disconnectBtn.setHorizontalAlignment(SwingConstants.LEFT);
            disconnectBtn.setAlignmentX(LEFT_ALIGNMENT);
            disconnectBtn.setMaximumSize(disconnectBtn.getPreferredSize());
            disconnectBtn.addActionListener(e -> onDisconnect.run());
            btnPanel.add(disconnectBtn);
        }
        else
        {
            JButton connectBtn = new JButton("Connect " + platformName);
            connectBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            connectBtn.setHorizontalAlignment(SwingConstants.LEFT);
            connectBtn.setAlignmentX(LEFT_ALIGNMENT);
            connectBtn.setMaximumSize(connectBtn.getPreferredSize());
            connectBtn.addActionListener(e -> onConnect.run());
            btnPanel.add(connectBtn);
        }

        card.add(btnPanel, BorderLayout.SOUTH);

        // BorderLayout reports an unbounded maximum size, which makes BoxLayout stretch the card; clamp it.
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        return card;
    }

    private JPanel createPublicMonitoringCard(String platformName)
    {
        JPanel card = new JPanel(new BorderLayout(5, 5));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 3, 0, 0, KICK_GREEN),
            new EmptyBorder(8, 10, 8, 10)
        ));

        JLabel platformLabel = new JLabel(platformName);
        platformLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        platformLabel.setForeground(Color.WHITE);
        card.add(platformLabel, BorderLayout.NORTH);

        JLabel statusLabel = new JLabel("Public monitoring - no account required");
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        statusLabel.setForeground(STATUS_ONLINE_COLOR);
        card.add(statusLabel, BorderLayout.CENTER);

        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
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
                LinkBrowser.browse(stream.getStreamUrl());
            }
        });

        JPanel headerRow = new JPanel();
        headerRow.setLayout(new BoxLayout(headerRow, BoxLayout.Y_AXIS));
        headerRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JLabel nameLabel = new JLabel(stream.getChannelName());
        nameLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setAlignmentX(LEFT_ALIGNMENT);
        headerRow.add(nameLabel);

        JLabel liveStatus = new JLabel(stream.isLive() ? "● " + stream.getViewerCount() + " viewers" : "Offline");
        liveStatus.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        liveStatus.setForeground(stream.isLive() ? STATUS_ONLINE_COLOR : STATUS_OFFLINE_COLOR);
        liveStatus.setAlignmentX(LEFT_ALIGNMENT);
        headerRow.add(liveStatus);

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

        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        return card;
    }

    private String escapeHtml(String s)
    {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
