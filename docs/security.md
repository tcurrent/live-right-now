# Security

Each connection flow uses a cryptographically random state value and a locally generated handoff secret. The browser receives only the SHA-256 proof of that secret. The plugin only accepts a callback from `127.0.0.1` that matches the active provider and state, and it redeems the one-time handoff code with the original secret.

Provider access tokens are never sent in a URL. OAuth access tokens are stored only in RuneLite secret configuration and are not written to logs.

[Open an issue](https://github.com/tcurrent/live-right-now/issues) to report any Security concerns.