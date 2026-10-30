> Historical development notes. For a new installation, follow [Cloudflare setup](../CLOUDFLARE_SETUP.md). Personal deployment instructions below are not setup requirements.

# Mindful Remote — Design Preview 1.5.0

Opt-in Android remote control built on pinned Mindful commit
`b6eb68d500463c54e1127b6cc7ad540f02ae0c8e` (GPL-2.0). This private repository is
an explicit source overlay; the cloud workflow checks out upstream and applies
`scripts/overlay-files.json`. The original Flutter/Android architecture remains.

See [Design Preview 1.5 implementation and verification](DESIGN_1_5.md) for the current app-wide UI changes and update identity.

## Architecture and retained behavior

Cloudflare Worker + D1 owns desired remote state. Authenticated MCP and the private
REST API edit it. A user-enabled Android foreground service syncs approximately
every 15 seconds. The phone validates the whole snapshot, durably commits it,
prepares local transitions and later acknowledges its revision. Existing immediate
flow remains:

`block_apps → desired state → phone sync → full validation/persistence → revision acknowledgement → overlay enforcement`

The proven immediate block representation is retained independently from new
schedule/preset rules. Longer immediate blocks are never shortened by shorter
requests. Remote state stays separate from upstream focus sessions, bedtime,
app/group limits and their existing storage. Ending or deleting a remote rule
falls through to those restrictions. The personal owner previously confirmed
Coca-Cola immediate blocking on Preview 2; this does not validate new scheduling.

## New behavior

- Future one-time rules with UTC start/end endpoints, delivered before their start.
- Structured weekly recurrence with ISO weekdays, IANA timezone and overnight
  windows. The phone computes occurrences indefinitely without a cloud horizon.
- Schedule CRUD and reusable preset CRUD/activation through 15 MCP tools.
  Study/Deep Work/Sleep/Driving are suggested names, not automatic app policies.
- Independent overlapping rules combine by union. Pausing/deleting one cannot
  release an app held by another remote rule or ordinary Mindful restriction.
- Temporary or indefinite schedule/preset pause and resume. A timed pause resumes
  locally without internet and does not restart the rule's original duration.
- Structured heartbeat, permissions, service/scheduler availability, local next
  transition, errors and desired-versus-applied status. Confirmed activity requires
  a fresh acknowledged phone observation; desired activity is not proof.
- Minimal existing Remote screen: connection, allowed apps/select-all, active
  blocks, schedules, presets, pause/resume, last sync, next transition and precise
  alarm access. App discovery remains off the UI thread and separate from status.

See [API/MCP and data model](API.md), [Samsung acceptance tests](SAMSUNG_TESTS.md),
and [phone setup](phone-setup.md).

## Device-local scheduling

`RemoteRuleEngine` is a pure calendar/union evaluator. `RemoteSnapshotParser`
validates complete responses before `RemoteStore` commits a revision. Existing
Keystore credentials/preferences are retained. `RemoteScheduler` prepares just
the next transition using one AlarmManager alarm and one in-process one-shot timer.
There is no new every-few-seconds calendar poll; the old one-second expiry loop
was removed. Network sync retains its existing delivery cadence and is not needed
at start/end. Native usage-event detection still uses upstream's existing launch
tracking strategy while enforcement is required.

Reboot/package replacement and clock/timezone/exact-alarm permission broadcasts
recompute from persisted state. Weekly rules follow their saved zone, not the
current travel zone. DST gaps move forward by the gap; overlaps use earlier start,
later end. Ordinary device clock changes move absolute-time enforcement as expected;
server/phone skew is reported and excessive skew prevents confirmed-active claims.

### Honest Android constraints

On Android 12+, user-granted Alarms & reminders access is needed for exact alarms.
Use `setExactAndAllowWhileIdle` if allowed and `setAndAllowWhileIdle` otherwise;
inexact mode is explicitly shown. Even exact idle alarms are rate-limited/batched
by Android, particularly closely spaced transitions. Short test windows while
awake do not establish Doze reliability. Scheduled rules can be received/persisted
in inexact mode; acknowledgement does not promise punctual future execution.

Android/Samsung can restrict background service starts, freeze/kill processes,
delay alarms, or cancel alarms when exact-alarm access is revoked. A user force-stop
can stop enforcement and alarms until reopening. Boot restoration occurs after
normal credential-protected storage becomes available, generally first unlock;
there is no pre-unlock guarantee. Keep the notification and battery exemption
configured. Service-start/alarm errors are recorded; there is no tamper-proof or
parental-control claim. See official [alarms guidance](https://developer.android.com/develop/background-work/services/alarms)
and [Doze guidance](https://developer.android.com/training/monitoring-device-state/doze-standby).

## Security and privacy

OAuth/PKCE, exact registered callbacks, CSRF checks, replay prevention, refresh
rotation and separate owner/device/signing secrets are preserved. No CORS. Device
tokens remain Android-Keystore encrypted; backups remain disabled. Protected
launcher/settings/dialer/permission-controller/Mindful packages remain unselectable.
All new definitions/activations validate current app opt-in. Local opt-out is
immediate, and the relay prunes package references in a new revision on next sync.

The phone credential has only its existing sync and a narrow pause/resume route;
it cannot use arbitrary control tools or widen app access. Phone UI overrides need
internet; existing received rules still transition offline. The relay stores names,
selected packages, desired rules, permission/readiness booleans and last heartbeat.
There are no screens/messages/notification contents, browsing history, keylogging
or general activity monitoring. Diagnostics remain bounded. No remote activity log
or push service was added; these optional items are deferred for correctness.

## D1 migration and rollout

Apply the additive migration before deploying this Worker:

```sh
cd relay
npx wrangler d1 migrations apply mindful-remote --remote
npx wrangler deploy
```

`0002_remote_planning.sql` adds a JSON desired-state column; it preserves immediate
blocks, revisions, app opt-in and all OAuth tables. Use the existing D1 binding and
configured secrets. Do not recreate the database or rotate credentials. Export a
D1 backup before applying a production migration. Local tests apply all migrations
in order to real SQLite. This remains one personal device on a small relay; no
paid model calls or new cloud service are introduced. Platform free-tier limits
still apply; there is no zero-cost guarantee for arbitrary usage/large query loads.

Deploy new relay code before relying on schedules; install the new phone APK before
creating them. A legacy phone can continue immediate blocks but cannot acknowledge
a revision containing planning definitions. A new phone cannot accept a legacy
snapshot after receiving v2 rules. Refresh ChatGPT's MCP tool discovery if cached
connection metadata still shows the four older tools; do not change credentials
unless authorization itself fails.

## Build and signing

The GitHub workflow runs relay tests/Wrangler dry-run, Dart analysis, remote widget
tests, native Kotlin/Android build, native JUnit tests and APK packaging, using
Flutter 3.35.7 and Java 17. The output is an arm64 personal debug preview, version
1.3.2 / code 132. Native java.time is desugared for the existing minimum Android API.
The runner artifact must be re-signed privately before delivering an update.

Keep the existing personal PKCS12 signing key (`mindfulremote`) outside GitHub.
Certificate SHA-256:
`e1675f775fc6a45d6db781881e0d51f178357c99e23c38a8c1804fd33274fa3f`.
Use the same package `com.mindful.android.remote.debug` and higher version code
so the owner can install over Preview 2/3 without clearing saved credentials or
permissions. Verify final v2/v3 signature, package, version and alignment. With
`apksigner`, pass `--ks-pass file:<password-file>` once; omit duplicate key-password
file arguments when the same one-line password is shared.

Do not retry the local Flutter build path previously rejected for attempting a
cloud-instance metadata request. Cloud Actions is the build environment.

## Validation status

Baseline immediate behavior was confirmed by the owner on the Samsung phone.
New automated tests cover calendar boundaries, recurrence, overnight/DST policy,
overlap, deletion, pause/resume, presets, offline calculations, reboot recomputation,
full-snapshot rejection/persistence gating, allowlist changes, upstream restriction
precedence, authentication and existing immediate-block regression behavior.

A passing cloud build is not a real-device test. Scheduled alarm precision,
Samsung background/reboot behavior, real offline overlays and battery consumption
remain unverified until [the manual checklist](SAMSUNG_TESTS.md) is completed.

## Verified cloud build and deployment — 2026-10-20

- Source commit: `3e4e2f230a6fd8ba5bfa0faf155b56ff509920cd`.
- [Final cloud run](https://github.com/RafailAndreou/FocusRelay/actions/runs/39425081367):
  30 relay tests, 24 native JUnit tests (zero failures/errors/skips), six widget
  tests, Dart analysis, Wrangler dry-run and APK build passed.
- Additive D1 migration applied after a private SQL export; existing revision and
  block data preserved. Standard migration bookkeeping records both migrations.
- Live Worker v0.2.0 deployed with the existing D1 binding and all three credentials
  inherited; deployment `REDACTED_DEPLOYMENT_ID`.
- Live read-only checks passed: health, credential separation, anonymous rejection,
  MCP OAuth requirement, all 15 tool schemas, list_schedules, list_presets and
  get_status. The existing ChatGPT OAuth connection still works. No synthetic
  heartbeat, app selection, schedule or preset was written to the real device.
- Final signed APK: `Mindful_Remote_1_3_0.apk`, 98,048,484 bytes, version 1.3.0/130,
  package `com.mindful.android.remote.debug`. Signature v2/v3 and 16 KiB native
  ZIP alignment verified with the existing personal certificate above.
- APK SHA-256: `7721069407a730d03487c89ae732310d22bab96a1615dc99a0f1e8588d286b70`.

This verifies cloud/source/package behavior only. New offline overlays, actual
alarms, Samsung Doze, reboot restoration and battery use remain unverified on a
physical phone. The last heartbeat during rollout was stale; reopen after install.

## MCP action discovery troubleshooting

If ChatGPT says **Authentication succeeded, action discovery failed**, owner
authorization completed but importing the MCP tool definitions did not. This
does not diagnose Android permissions or indicate that the device token is wrong.

The discovery compatibility patch replaces untyped `oneOf`/`const` timing
definitions with an explicitly typed object and string enums. The wire arguments
remain unchanged: `timing.type` is `once` or `weekly`. Server-side
`timing()` still enforces conditional required fields and rejects mixed modes.
All fifteen tools, OAuth/PKCE, allowlist enforcement and revision semantics remain.

After the relay patch is deployed, retry connecting with the existing MCP URL
and owner secret. No APK reinstall or credential rotation is needed. Successful
tool enumeration in relay tests does not establish successful import by ChatGPT;
confirm discovery in the actual ChatGPT connection before claiming resolution.

## Verified foreground/overlay update — 1.3.1 (2026-10-22)

- Built source: `0d95f9094b127110d8cd57a32ddd045069bbd65c`.
- [Cloud run](https://github.com/RafailAndreou/FocusRelay/actions/runs/39681802328)
  passed 35 relay tests, 32 native JUnit tests (zero failures/errors/skips), six
  Flutter screen tests, Dart analysis, Wrangler dry-run and the Android build.
- Signed `Mindful_Remote_1_3_1.apk`: version 1.3.1/code 131, package
  `com.mindful.android.remote.debug`, 98,048,484 bytes. The existing personal
  certificate is retained; signature v2/v3 and all five uncompressed native
  libraries' 16 KiB ZIP alignment verified.
- APK SHA-256: `95156e6bcc3dd4177e75202b0a32d39f52a3eaa35280d08b67e8fefcaeb5cb77`.
- Updates usage-event fallback, late-event replay and overlay ordering for both
  local and remote blocking. No relay deployment, migration, credential rotation
  or APK uninstall is required.
- Actual prompt overlays on the Samsung phone, accessibility interactions,
  lock/unlock and expiry remain subject to physical-device verification. The
  earlier desired-rule/service readiness status does not prove a visible overlay.


## Verified overlay cleanup update — 1.3.2 (2026-10-22)

- Built source: `0ab6f5578ac4e693c68a6a9506fefd9fe1485480`.
- [Cloud run](https://github.com/RafailAndreou/FocusRelay/actions/runs/39687173083)
  passed 35 relay tests, 43 native JUnit tests (zero failures/errors/skips), six
  Flutter screen tests, Dart analysis, Wrangler dry-run and the Android build.
- Signed `Mindful_Remote_1_3_2.apk`: version 1.3.2/code 132, package
  `com.mindful.android.remote.debug`, 98,048,484 bytes. Signature v2/v3,
  the same personal certificate as 1.3.1 and all five uncompressed native
  libraries' 16 KiB ZIP alignment verified.
- APK SHA-256: `36417dad86feb03b2e0cfa5040f40ba04c69f335000996a115be5110f6d2e537`.
- Retains overlay registration until removal even before attachment, rejects
  callbacks after disposal, directly cleans up revoked/expired remote windows,
  releases idle tracker bindings and keeps system navigation outside the overlay.
- Install over 1.3.1 without uninstalling or re-pairing. No relay deployment,
  migration or credential change is needed. No new test block was sent to the
  owner's phone during this investigation.
- Physical Samsung navigation, remote revocation and expiry still need a short
  controlled device test; automated results do not establish visible dismissal.


## Verified unified Blocking preview — 1.4.0 (2026-10-22)

- Built source: `27ddd52a0a64cc6e170ce9e8e533efc0f3e3be95`.
- [Cloud run](https://github.com/RafailAndreou/FocusRelay/actions/runs/39708594211)
  passed 35 relay tests, 56 native JUnit tests (zero failures/errors/skips),
  14 Flutter tests, Dart analysis, Wrangler dry-run and APK compilation.
- Charcoal/mint Blocking, Rules, Activity and Settings tabs; local timeline;
  Add Rule choice sheet followed by Timing → Apps → Review. Independent
  weekly/overnight/multiple-window schedules, shared or per-app daily allowances,
  and saved Quick Block configuration. Pause/delete never changes relay state.
- The original dashboard, statistics, bedtime, remote-control setup and advanced
  settings remain accessible. Local rules do not sync into relay schedules;
  remote rules remain visible separately. Activity records overlay creation
  events on this phone; screenshots use test fixtures, not the owner's live data.
- Signed `Mindful_Design_1_4_0.apk`: version 1.4.0/code 140,
  `com.mindful.android.remote.design`, 98,154,980 bytes. Signature v2/v3 and
  all five uncompressed native libraries' 16 KiB alignment verified.
- APK SHA-256: `95f0817ff83c489bc2857be082371274465df7502a270c356f87b7196a04c88c`.
- NEW preview certificate SHA-256:
  `0316f9855e3ed2545a7ffbb84d3f3145705060b0e7c75a0556823640af2aebe1`.
  The previous signing-key file was unavailable. This separate package installs
  beside 1.3.2; it cannot update the older package. Keep the working installation.
  A private signing backup is supplied separately and must never enter GitHub.
- Grant permissions to this preview and test local rules first; remote pairing
  is optional. Avoid simultaneous enforcement from both installations during
  comparison. No live relay deployment, migration, credential change or new
  phone test block was performed during this work.
- Samsung overlay/navigation behavior, clock/alarm precision, Doze and reboot
  restoration still require physical-device checks. Automated tests and logical
  rule status do not establish that a blocking screen appeared on the phone.


## Device Modes extension

The `device-modes` branch adds an additive D1 migration, schema-3 phone envelope and structured mode/exception/sequence/usage/history tools. Existing remote blocks, schedules, presets and OAuth/PKCE remain supported. See [Device Modes](../device-modes/README.md), including upgrade order, local consent, acknowledgement, Android controls and the exact MCP schemas.
