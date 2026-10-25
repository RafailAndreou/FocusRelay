# Privacy

FocusRelay is a self-hosted personal productivity app. Local usage/activity data stays on the phone; the app uses Android usage access to determine foreground activity and enforce selected restrictions. Its blocking overlay does not require screen recordings, messages, notification contents or keylogging.

If you enable remote control, the configured Cloudflare Worker/D1 relay receives your selected app labels and package names, remote rules/presets, connection heartbeat, permission/service diagnostics and revision acknowledgments. The relay stores current device state and OAuth registration/replay/rate-limit records. Authorized AI clients can retrieve selected app information, manage remote rules and inspect connection status. The AI provider's own privacy terms apply to anything shared with that provider.

Cloudflare processes relay requests and stores D1 data in the account hosting the relay. This repository does not provide a hosted service and contains no analytics account. Worker observability is disabled in the example configuration; the Cloudflare platform may still maintain operational/security metadata under its own policies. Avoid enabling request-body or authorization-header logging.

Disable remote control to stop app synchronization. Removing opt-in apps prunes their remote rules on the next successful sync. Disconnecting does not delete stored relay data: the relay owner can delete the deployed Worker/D1 database when no longer needed. Android application data can be removed through Android Settings. Backups/exports should be treated as private.
