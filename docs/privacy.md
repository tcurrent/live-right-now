# Privacy

Live Right Now stores tracked channel names, connected account names, OAuth access tokens, and notification history in local RuneLite configuration. OAuth access tokens are marked as secret configuration values.

The plugin communicates with `id.twitch.tv`, `api.twitch.tv`, `id.kick.com`, `api.kick.com`, the Live Right Now OAuth Proxy for Kick token exchange and handoff, and `localhost:4646` during a connection flow. Twitch authorization returns an access token in the browser fragment, which the localhost callback page forwards directly to the local listener; it is not sent to the proxy. It sends no RuneLite account data, game state, analytics, or telemetry.

For Kick, the OAuth Proxy exchanges the authorization code and temporarily stores an encrypted handoff record for no more than five minutes. It does not log OAuth codes, access tokens, refresh tokens, or handoff secrets.

Disconnecting or resetting the plugin removes local credentials immediately and attempts to revoke the provider access token. Users can also revoke access from their Twitch or Kick account settings.