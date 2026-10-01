# Security

Each connection flow uses a cryptographically random state value and a locally generated handoff secret. Twitch and Kick redirect their authorization codes to the plugin's local callback at `http://localhost:4646/callback`. Kick also uses a locally generated PKCE verifier. The plugin validates the active flow and state, sends each authorization code to the proxy for exchange, and redeems a one-time handoff code with the original secret. The Twitch and Kick client secrets remain in the proxy.

Provider access tokens are never sent in a URL. OAuth access tokens are stored only in RuneLite secret configuration and are not written to logs.

Resetting the plugin configuration also attempts to revoke any previously cached Twitch or Kick access token before the local credential is discarded.

[Open an issue](https://github.com/tcurrent/live-right-now/issues) to report any Security concerns.