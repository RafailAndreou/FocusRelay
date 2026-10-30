# Device Modes implementation design

Baseline: FocusRelay main 5952affa75d8e1a7185b484cf95bd0cc8b8821cd.

## Existing architecture and reuse

The repository is an overlay over pinned upstream Mindful. Flutter's BlockingController uses the existing background MethodChannel. RemoteControlService polls the HTTPS relay; RemoteStore validates and commits a complete snapshot; only a later heartbeat acknowledges its revision after scheduler/tracker preparation. RemoteRuleEngine evaluates persisted rules; RemoteScheduler keeps a next-transition alarm and in-process timer. RestrictionManager combines restrictions, and OverlayManager owns registered WindowManager windows with cancellation and expiry guards. LocalRules, existing schedules/presets and OAuth/PKCE remain independent.

Reuse: the authenticated transport, CAS revision updates, timezone/DST calendar evaluator, Keystore credentials, remote scheduler, tracker, overlay cleanup, app icons and UI styling. Extend rather than replace these components.

## Minimum schema changes

One additive D1 migration adds assistant_state (version 1 JSON: templates, instances, exceptions) and a bounded remote_events table. Existing remote_state version 2, immediate blocks and OAuth tables are unchanged. New phones advertise envelope schema 3 plus explicit local mode/actions/usage consent. Legacy clients receive the original version 2 envelope and cannot acknowledge revisions containing mode data. The Android parser accepts both schemas; version 3 adds a validated deviceModes member. Mode definitions are reusable templates; activations snapshot them and retain absolute timing, independent of later template edits.

Allow-list restrictions are evaluated dynamically against currently installed, opted-in, mode-eligible packages; no expanded complement is persisted. Essential phone apps and FocusRelay are excluded again at enforcement time. System/preinstalled apps and recognizable banking/authentication/password/security apps are conservatively excluded from bulk mode control; classification is a conservative heuristic, not a universal financial-app detector. The owner can add explicit exclusions. Unknown/non-opted-in packages never become blocking targets. Allow-lists must be nonempty. Sessions last at most seven days; recurring activations have a finite 30-day lease. Sequences are at most 16 absolute, contiguous instances with a total duration at most seven days.

Exceptions belong to one activation, last at most one hour, and never weaken other modes, local rules or legacy remote blocks. Union wins across restrictions. Stopping a mode locally creates a durable tombstone that takes precedence over in-flight/redelivered snapshots. Emergency stop disables mode execution locally, including queued sequence steps, without altering legacy blocking settings.

## Device actions and ownership

Actions require a separate local consent switch, plus Android permissions. Brightness is global only with WRITE_SETTINGS and manual brightness already selected; automatic brightness is left intact. Media/ringtone volumes use official AudioManager APIs; ringtone volume cannot silently switch to silent mode. Fixed-volume devices report unsupported. Alarm volume is never changed.

An app-owned AutomaticZenRule (Android 10/API 29+) contributes DND without changing global policy or disabling another app/user's DND. Older Android versions report DND unsupported. Notification policy is restricted to the app's DND contribution; per-app notification interception/filtering is unsupported. No notification-content listener or accessibility workaround is added.

Brightness/volume leases save baseline and intended last-written value durably before side effects. Overlapping modes choose the lowest requested value. Writes/restores use observed-value comparison; a different observed value relinquishes ownership until all contributing modes end, so manual changes win. Same-value changes and unobserved ABA changes are indistinguishable and explicitly not claimed detectable. Reconciliation runs on sync, mode boundaries, boot/package replacement and foreground recovery. Restore failures retain the journal for retry; Android force-stop delays all recovery until reopening. No promise of recovery while the OS forbids app execution.

AlarmClock.ACTION_SET_ALARM is a user-visible handoff from the foreground UI, not a silently confirmed background alarm. A remote alarm request is reported as needs_user_confirmation; handing it to Clock is not proof Clock scheduled it. Only a next-24-hours request is offered to avoid the intent's ambiguous date semantics.

## Status, usage and history

Desired state is never confirmation. Status combines matching revision, fresh heartbeat, permissions, scheduler/tracker readiness and the phone's observed mode states/blocks. App-action errors are reported separately; a persisted revision does not imply an optional setting or alarm succeeded. Device usage sharing is separately opt-in and limited to selected apps plus aggregate screen-time estimates when Android event coverage supports them. Missing permission/data returns unavailable/null, never fabricated zero. Launch counts remain null; foreground events/visible blocking overlays are not presented as exact launches or all attempted openings.

A bounded local log records only remote-control transitions/actions and visible remote blocking windows; upload deduplicates IDs into a 30-day/1,000-event relay log. Queries require explicit bounded dates and cursor pagination. No screenshots, keystrokes, notification/message contents or browsing history.

## Stages

1. Additive relay schema, pure mode model/evaluation and validation tests.
2. Android schema/allow-list enforcement, scheduler, opt-in, exceptions and offline recovery.
3. Flutter Modes UI and native bridge with immediate local emergency stop.
4. Journaled device actions, usage reporting and event history.
5. Cross-layer regressions, documentation, Flutter analysis and Android build/native tests.

Development is isolated on device-modes. No live relay deployment, database migration or phone commands are needed to implement/test this change.
