# Security

Each connection flow uses a cryptographically random state value. Twitch uses the implicit grant and returns its access token in the URL fragment. The localhost callback page removes the fragment and POSTs the token and state to the active local flow; the listener validates the state and accepts the token once. The token is not sent to the hosted proxy. Kick redirects its authorization code to the same local callback. RuneLite generates the PKCE verifier and sends the code and verifier to the proxy, which performs the token exchange using the Kick client secret and returns a one-time handoff code. The Kick client secret remains in the proxy.

Twitch's access token is briefly present in the browser URL fragment, which the callback page removes before forwarding it locally. Fragments are not included in the callback's HTTP request. OAuth access tokens are stored only in RuneLite secret configuration and are not written to logs.

Resetting the plugin configuration also attempts to revoke any previously cached Twitch or Kick access token before the local credential is discarded.

[Open an issue](https://github.com/tcurrent/live-right-now/issues) to report any Security concerns.