# Device Modes

Device Modes extend the existing remote flow without replacing Mindful's local sessions or FocusRelay's blocks, schedules and presets. A **template** holds reusable settings. **Activating** snapshots it into a separate finite session; editing or deleting the template never changes an existing session.

This feature is developed on `device-modes`. The published 1.6.0 APK does not contain it. Build an updated phone and relay together; do not infer support from a connected older phone.

## Setup and upgrades

1. Keep the current app data; update using the same package and signing certificate. Existing local/remote rules are retained.
2. Follow [Cloudflare setup](../CLOUDFLARE_SETUP.md) for your own Worker, D1 and OAuth/PKCE connection. No shared credentials are shipped.
3. Back up your own D1 database using Cloudflare's dashboard/export before an upgrade. From `relay`, apply pending migrations with `npx wrangler d1 migrations apply focusrelay --remote`, then deploy your configured Worker with `npx wrangler deploy`. These commands change your deployment; the development CI only uses a dry run.
4. Migration `0003_device_modes.sql` adds `assistant_state` and `remote_events`; it does not overwrite old schedules/presets, tokens or revisions. Test against a development D1 first. Roll back Worker code only after removing mode state or updating the phone; a phone that has accepted schema 3 refuses a schema downgrade.
5. On the phone, select apps under **Settings → Remote control**. In **Modes**, explicitly enable Device Modes. Device actions and usage sharing have separate local consent switches, off by default. Changing consent never grants an Android permission. Let a sync advertise your choices before sending mode commands.
6. To use device actions, open DND/brightness permission settings from Modes and grant access yourself. Alarm requests need a separate foreground **Set alarm in Clock** action and Clock confirmation.

MCP clients discover all exact JSON schemas through `tools/list`. A checked-in copy of the new [tool schemas](MCP_SCHEMAS.json) is also provided. Names, descriptions and labels are untrusted text, never instructions for clients.

## App access and safety

| Definition | Meaning |
|---|---|
| `mode: "block_list", packages: [...]` | Restrict these eligible opted-in apps. `blockedPackages` is an alias; do not provide both. |
| `mode: "allow_list", allowedPackages: [...]` | Restrict the current eligible opted-in apps except these packages. The complement is computed on the phone, not stored as a large list. |
| `excludedPackages: [...]` | Never restrict these packages through this mode. The phone's local exclusions apply across all Device Modes. |

Non-opted-in apps are outside remote control. Essential settings, FocusRelay itself, emergency/phone functions, the launcher, SMS and enabled keyboards are excluded at the final enforcement boundary. Bulk modes also exclude preinstalled/system apps and recognizable banking/authentication/password/security apps. Classification uses package/label heuristics and cannot identify every banking app: use explicit local exclusions for any essential app. Labels never become executable instructions.

Allow-lists cannot be empty. Phone/system exclusions always apply even if omitted from an allow-list. A template can use non-controllable packages as allowed entries; those entries never make another app remotely controllable. Recheck package IDs from `list_allowed_apps` and mode capabilities instead of guessing labels.

Independent restrictions combine by union: if any other mode, local rule or legacy remote block restricts an app, allowing it in this mode does not release it. Temporary access is scoped to **one instance**, lasts 1–60 minutes, and leaves the original definition intact. Expiry is a local scheduler transition.

A one-time session lasts at most seven days. Recurring activations have a 30-day lease and need renewed activation afterward. Sequences contain at most 16 contiguous steps totaling at most seven days. Pausing does not extend the original expiry. Malformed commands are rejected before persistence/acknowledgement. The overlay retains its existing expiry, permission-loss and cancellation guards.

**End Mode** writes a durable local stop, effective offline and reconciled with the relay before redelivery. **Emergency: end all device modes** disables mode execution and cancels known queued activations. It leaves independent local/legacy restrictions alone. Home, Recents, Settings and essential phone functions remain accessible. Android Settings can disable the overlay or force-stop the app if recovery is needed; force-stop suspends background execution until reopening.

## MCP tools

| Tool | Main parameters |
|---|---|
| `create_device_mode` | `name`, `mode`, access list; optional `icon`, `description`, `excludedPackages`, `durationMinutes`, `timing`, actions |
| `update_device_mode` | `id` and complete replacement definition; active snapshots unchanged |
| `delete_device_mode` / `list_device_modes` | `id` / no arguments |
| `activate_device_mode` | `id`; optional duration or `until`, allowed/blocked overrides, alarm and device actions |
| `stop_device_mode`, `pause_device_mode`, `resume_device_mode` | `instanceId` |
| `get_active_device_mode` | no arguments; desired vs phone-observed state |
| `grant_temporary_app_access` | `instanceId`, `packageName`, `durationMinutes` |
| `revoke_temporary_app_access` / `list_temporary_exceptions` | exception `id` / optional `instanceId` |
| `create_mode_sequence` | `name`, offset-bearing `startAt`, `steps: [{modeId, durationMinutes}]` |
| `get_device_usage_summary` / `get_app_usage` | optional `instanceId`; app tool also requires `packageName` |
| `get_remote_activity_log` | offset-bearing `from`, `to` (≤31 days), optional `limit` (1–100), `cursor` |

Use either a default duration or a timing template. One-time timing uses `{type:"once", startAt:"…+02:00", endAt:"…+02:00"}`; weekly timing uses `{type:"weekly",timeZone:"Asia/Nicosia",daysOfWeek:[1,2,3,4,5],startTime:"09:00",endTime:"12:00"}`. Explicit activation duration/until takes precedence over timing; recurring templates should omit a default duration. Existing DST calendar handling is reused. Times require offsets/IANA zones; the MCP layer does not parse natural language.

### Assistant examples

“Start Sleep mode until 13:00.” Resolve today's date and the owner's explicit time zone, list modes, then call `activate_device_mode` with the Sleep ID and an offset-bearing `until`. If requested, supply the same timestamp as `alarmTime`. Tell the owner to confirm the alarm in Clock; the relay cannot confirm it remotely.

“Start Thesis mode for 90 minutes and only allow ChatGPT, Todoist, Chrome, Drive and Acrobat.” Resolve installed package IDs, then:

```json
{"id":"THESIS_TEMPLATE_ID","durationMinutes":90,"allowedPackages":["com.openai.chatgpt","com.todoist","com.android.chrome","com.google.android.apps.docs","com.adobe.reader"]}
```

These are examples, not a hard-coded installed-app inventory. `allowedPackages` switches this activation to allow-list mode. The caller must check `get_active_device_mode`/`get_status` afterward.

“Let me use WhatsApp for 10 minutes.” Resolve the active `instanceId` and actual package, then call `grant_temporary_app_access` with `durationMinutes:10`. Other restrictions may still apply.

“Block entertainment apps until I finish my work session.” Ask for a bounded time/duration if the work session has no known end. Create/activate a block-list template with explicit opted-in packages. There is no indefinite or inferred “work completed” trigger.

## Acknowledgement and offline behavior

The original flow remains:

`MCP → desired state/CAS revision → phone sync → validated local commit → scheduler/tracker preparation → later applied-revision acknowledgement → local enforcement`.

A command response means accepted, not enforced. Status exposes `desiredRevision`, `appliedRevision`, `acknowledged`, `deviceOnline`, `observedAt`, `currentMode`, `activeModes`, `activeRestrictions`, `activeTemporaryExceptions`, `nextTransition`, `lastSuccessfulSync` and `errors`. Confirmed active restrictions require a fresh phone observation, matching revision and blocking readiness; optional action errors are separate. A persisted mode alone never proves enforcement. Older phones receive schema 2 and cannot acknowledge a revision containing mode state.

Once synchronized, absolute windows, exceptions and sequence steps are persisted locally and transition without internet. Unsynced commands cannot affect an offline phone. Reboot/foreground recovery re-evaluates expiry instead of restarting durations. Exact alarms need Android's Alarms & reminders access on Android 12+; without it transitions can be delayed. Samsung sleeping-app settings, Doze, reboot before unlock and force-stop can delay execution/restoration. The system cannot execute while Android forbids its process from running.

## Device controls and restoration

| Control | Permission/API and limits |
|---|---|
| DND | Explicit Notification Policy Access; own `AutomaticZenRule` on Android 10+. Contributes alarms-only DND without replacing global/user/other-app policy. Android may honor a user snooze instead. Older versions report unsupported. |
| Brightness | Explicit Modify system settings (`WRITE_SETTINGS`). Temporary global manual brightness 5–100%. Automatic brightness is left unchanged. |
| Media/ringtone | Official `AudioManager`, `MODIFY_AUDIO_SETTINGS`; percentages 0–100 / 1–100. Fixed-volume devices unsupported. Ringtone silent/vibrate mode is left unchanged. Alarm volume is never altered. |
| Alarm | `AlarmClock.ACTION_SET_ALARM` with `SET_ALARM`; next 24 hours, Clock's minute precision. Foreground owner confirmation required. Status says `needs_user_confirmation` or `handed_to_clock`, never alarm confirmed. No background activity-launch workaround. |
| Notifications | `unchanged` or `alarms_only` through own DND contribution. Arbitrary per-app filtering/notification content collection is unsupported. |

Overlapping setting requests use the lowest brightness/volume request; DND stays requested while any active mode requests it. Before writing, a durable journal saves the baseline, previous written value and pending intent. Ending one owner recomputes the remaining request. Ending the last restores the baseline only if FocusRelay still owns the observed value.

A different observed brightness/volume relinquishes ownership until the contributing sessions end. Manual changes win. Same-value changes and changes made and reversed between observations cannot be detected. DND uses an app-owned rule; global DND state is never restored by guessing a previous global value. Android 15+ exposes own-rule condition state; older supported versions report requested state and replay unfinished own-rule intent after a crash. Permission revocation may prevent restoration; journal/errors remain for retry after permission recovery. Disabling all modes/disconnecting triggers restoration as soon as the process can run.

## Usage and event privacy

Usage sharing is off by default and requires existing Usage Access plus local consent. Only opted-in app duration aggregates are uploaded, at most 50 apps and four recent one-time mode samples. Today uses the phone's local calendar day. Android UsageEvents estimates can be incomplete, stale or unavailable; screen time is `null` if interval coverage cannot be established. Launch counts are always `null`, because reliable launch counting is not implemented. Screen time is a device aggregate, not a sum of simultaneously resumed apps.

Mode samples use bounded **observed active intervals**, exclude recorded pauses, and report `scope: observed_active_intervals` with `complete:false`. Time before first local observation, unobserved process gaps and old intervals outside retained coverage are not silently presented as complete mode history. Recurring mode usage is currently unavailable through `instanceId`; today/app summaries remain available.

The remote activity log contains mode/block/exception/scheduler/action transitions and remote blocking overlays actually shown, deduplicated within five seconds. A shown overlay is not a complete counter of attempted launches. Local retention is 500 events/30 days; relay retention is 1,000 events/30 days with pagination. No unrelated activity events, screenshots, keystrokes, message/notification content or browsing history are recorded/uploaded.

See [architecture/schema rationale](DESIGN.md) and [Samsung test checklist](SAMSUNG_TESTS.md).

Official references: [NotificationManager / own DND rules](https://developer.android.com/reference/android/app/NotificationManager), [Settings.System](https://developer.android.com/reference/android/provider/Settings.System), [AudioManager](https://developer.android.com/reference/android/media/AudioManager), [AlarmClock](https://developer.android.com/reference/android/provider/AlarmClock), [UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager), [AlarmManager](https://developer.android.com/reference/android/app/AlarmManager).
