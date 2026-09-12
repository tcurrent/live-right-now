# Live Right Now

A RuneLite plugin that tracks specific Twitch and Kick streamers and delivers in-game banner popups, chat alerts, and desktop notifications when they go live.

<p align="center">
  <img src="docs/images/scrying_pool.png" alt="Live Right Now" width="220" />
</p>

## Features

- **Multi-Platform Support:** Track selected streamers on Twitch and Kick.
- **Persistent Session Tracking:** Receive one alert per broadcast, including streams already live when first configured.
- **In-Game Chat Alerts:** Plain-text game chat messages with no title clutter.
- **Desktop Notifications:** Optional RuneLite desktop notifications.
- **Twitch Connection:** Device authorization through Twitch's activation page.
- **Public Kick Monitoring:** Kick channels are checked without requiring a Kick account connection.

> **Notification behavior:** The plugin alerts once for each live broadcast, including a broadcast already in progress when you first add or configure a streamer. It remembers the broadcast across RuneLite logins and restarts, so the same live stream will not alert repeatedly. A new alert is sent when the streamer starts a new broadcast. If several new broadcasts are detected together, they are combined into one notification.

## Configuration

1. In the RuneLite sidebar, open the **Live Right Now** settings panel.
2. Click **Connect Twitch**, then authorize the displayed Twitch device code in your browser.
3. Add comma-separated usernames under **Tracked Streamers** in the **Twitch** and/or **Kick** sections (e.g. `b0aty, odablock, paymoneywubby`). Kick monitoring is public and does not require a Kick account connection.
4. (Optional) Customize your in-game chat or desktop notification preferences under **Notifications**.
