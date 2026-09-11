# Live Right Now

A RuneLite plugin that tracks specific Twitch and Kick streamers and delivers in-game banner popups, chat alerts, and desktop notifications when they go live.

<p align="center">
  <img src="docs/images/scrying_pool.png" alt="Live Right Now" width="220" />
</p>

## Features

- **Multi-Platform Support:** Track streamers on both Twitch and Kick simultaneously.
- **Selective Tracking:** Specify exact channel usernames you care about rather than all accounts you follow.
- **In-Game Popup Banner:** Combat Achievement style banner at the top of the screen when a streamer goes live.
- **In-Game Chat Alerts:** Formatted chat box messages with platform-specific branding.
- **Desktop Notifications:** Tray/OS notifications via RuneLite's Notifier.
- **OAuth Connection:** One-click account connection.

> **Notification behavior:** The plugin alerts once for each live broadcast, including a broadcast already in progress when you first add or configure a streamer. It remembers the broadcast across RuneLite logins and restarts, so the same live stream will not alert repeatedly. A new alert is sent when the streamer starts a new broadcast. If several new broadcasts are detected together, they are combined into one notification.

## Configuration

1. In the RuneLite sidebar, open the **Live Right Now** settings panel.
2. Click **Connect Twitch** and/or **Connect Kick** to authorize your account.
3. Add comma-separated usernames under **Tracked Streamers** in the **Twitch** and/or **Kick** sections (e.g. `b0aty, odablock, paymoneywubby`).
4. (Optional) Customize your banner, in-game chat, or desktop notification preferences under **Notifications**.
