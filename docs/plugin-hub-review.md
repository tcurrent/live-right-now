# Plugin Hub Review Notes

## Network destinations

| Destination | Purpose |
| --- | --- |
| `id.twitch.tv` | Token validation and revocation |
| `api.twitch.tv` | Stream status lookup |
| `id.kick.com` | Token revocation |
| `api.kick.com` | Account and channel lookup |
| Live Right Now OAuth Proxy | Kick authorization-code exchange and handoff redemption |
| `localhost:4646` | Temporary local Twitch token and Kick authorization-code callback |

## OAuth permissions

Twitch uses implicit grant with no privileged scopes; the plugin uses the issued user token only to identify the connected account and call the streams API. Kick requests `user:read` and `channel:read` to identify the account and look up tracked channels. The plugin does not request chat, moderation, broadcasting, email, or write permissions.

## Data handling

OAuth tokens, account names, channel names, and notification history remain in local RuneLite configuration. Twitch's access token is forwarded from the browser callback page to the local listener, not to the proxy. The plugin does not transmit RuneLite account data, game state, analytics, or telemetry. The proxy retains Kick handoff data for at most five minutes and does not log credentials.