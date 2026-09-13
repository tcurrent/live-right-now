# Privacy

Live Right Now stores tracked channel names, connected account names, OAuth access tokens, and notification history in local RuneLite configuration. OAuth access tokens are marked as secret configuration values.

The plugin communicates only with `id.twitch.tv`, `api.twitch.tv`, `id.kick.com`, `api.kick.com`, the Live Right Now OAuth Proxy, and `127.0.0.1:4646` during a connection flow. It sends no RuneLite account data, game state, analytics, or telemetry.

The OAuth Proxy performs a one-time authorization-code exchange and temporarily stores an encrypted handoff record for no more than five minutes. It does not log OAuth codes, access tokens, refresh tokens, or handoff secrets.

Disconnecting removes local credentials immediately and attempts to revoke the provider access token. Users can also revoke access from their Twitch or Kick account settings.