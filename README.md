# Live Right Now

A RuneLite plugin that tracks selected Twitch and Kick streamers and alerts you in-game when they go live.

<p align="center">
  <img src="docs/images/scrying_pool.png" alt="Live Right Now" width="220" />
</p>

## Features

- **Twitch and Kick Support:** Monitor selected streamers on both platforms.
- **Notifications:** Send alerts to chat, your desktop, or both.
- **Persistent Alerts:** The plugin remembers broadcasts across RuneLite logins and restarts.
- **Simple Setup:** Connect your Twitch and Kick accounts with one click in your browser.

> **Notification behavior:** The plugin alerts once for each live broadcast, including a broadcast already in progress when you first add or configure a streamer. It remembers the broadcast across RuneLite logins and restarts, so the same live stream will not alert repeatedly. A new alert is sent when the streamer starts a new broadcast. If several new broadcasts are detected together, they are combined into one notification.

## Configuration

1. In the RuneLite sidebar, open the **Live Right Now** settings panel.
2. Click **Connect Twitch** and/or **Connect Kick** to authorize your accounts.
3. Add comma-separated usernames under **Tracked Streamers** in the **Twitch** and/or **Kick** sections (e.g. `b0aty, odablock, paymoneywubby`).
4. Customize your in-game chat or desktop notification preferences under **Notifications**.

## How authorization works

Clicking **Connect Twitch** or **Connect Kick** opens your browser to the [Live Right Now OAuth Proxy](https://github.com/tcurrent/live-right-now-oauth-proxy). It performs the confidential authorization-code exchange without exposing a provider access token in the browser or callback URL. The plugin's listener binds only to `127.0.0.1:4646`, accepts a short-lived single-use handoff code tied to a random state value, then redeems it with a secret that never leaves RuneLite.

See [privacy documentation](docs/privacy.md), [security documentation](docs/security.md), and the [Plugin Hub review notes](docs/plugin-hub-review.md).
