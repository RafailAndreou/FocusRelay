> Historical development notes. For a new installation, follow [Cloudflare setup](../CLOUDFLARE_SETUP.md). Personal deployment instructions below are not setup requirements.

## 1.5.1 — stable rule switches

- Give the off-state thumb and track outline explicit contrasting colours across dark, light and AMOLED themes, including the pending-save state.
- Save each rule toggle independently. Other switches retain their enabled appearance and remain usable while a different rule saves.
- Retain each rule row by its ID and hold the requested switch value until a post-write state refresh completes.
- Restore the saved value and report an error when a toggle fails.
- Keep the Design Preview package and saved signing certificate; version code 151 installs over 1.5.0.

## 1.5.0 — coherent Design Preview interface

- Neutral surfaces and restrained mint, with custom launch/setup, shared feature chrome, settings, About and dialogs.
- Quick Block card opens apps; Timer edits duration separately. Existing rules open apps first with unsaved-change protection.
- Installed app icons on rules, timeline, Quick Block, allowances and activity; responsive exact +N groups and labelled fallbacks.
- Minimal native blocker/reminder presentation; preserve system navigation, cleanup and expiry guards.
- Replace the old-dashboard detour with focused usage, Bedtime and notification pages while retaining advanced functions.
- Remove the decorative country slogan; retain original attribution and bundle the GPL-2.0 license.
- Same Design Preview package/certificate, version code 150. Physical Samsung acceptance remains pending.
- See [implementation and verification](DESIGN_1_5.md).

## 1.4.0 — unified Blocking interface

- Charcoal/mint interface with Blocking, Rules, Activity and Settings tabs.
- Timeline of today’s local schedule windows; remote state remains separately visible.
- Add Rule choice sheet followed by timing, apps and review screens.
- Independent local schedules with multiple windows, weekdays and overnight handling.
- Shared or per-app daily usage allowances, counted from device midnight.
- Saved Quick Block setup; running quick blocks can be stopped independently.
- Local rule edits never overwrite relay snapshots, upstream limits or pairing.
- New local blocking-event history; existing analytics and dashboard remain accessible.
- The design preview uses a separate package, so the working 1.3.2 installation is retained.

# 1.3.2 overlay cleanup and navigation

- Remove the WindowManager registration even before View attachment. The 1.3.1
  attachment check could drop ownership without unregistering a pending window.
- Make overlay teardown terminal so late tracking/reminder work cannot re-add it.
  Revalidate remote authorization before adding a queued window.
- Revoke displayed remote overlays immediately on state refresh, independently
  of foreground detection. A one-shot window-expiry guard also cleans up without
  waiting for a new app launch. Release the remote service binding when no remote
  block remains; keep a tracker usable while another client still binds it.
- Remove immersive/hide-navigation and unrestricted window layout flags. Reserve
  system-bar and cutout insets so notification shade and navigation remain usable.
- Add deterministic window-registration and disposal regressions. Physical
  Samsung navigation, unblock and expiry still require a controlled acceptance test.
- Version 1.3.2/code 132 uses the same personal certificate as the supplied 1.3.1.

# 1.3.1 foreground/overlay fix

- Keep the existing 750 ms usage-event tracking active even while accessibility
  is connected. Accessibility broadcasts only trigger a fresh query. Remote
  updates, unlock and emergency-pause recovery no longer replay cached app names.
- Re-read and deduplicate a five-second lifecycle tail to tolerate delayed event
  delivery. Do not resurrect older packages when the current activity pauses.
- Confine overlay window state to the main looper; invalidate stale queued
  show/dismiss requests and retain an unchanged visible restriction on refresh.
- Exclude Mindful itself using its activity lifecycle and release overlays when
  no foreground app is available. These changes cover local and remote blocks.
- Preserve credentials, selected apps, schedule state, package and signing key.
  Install 1.3.1/131 over 1.3.0; no relay deployment or new permission is needed.
- Device verification remains necessary: test Reddit/Coca-Cola launch, switching
  to Mindful, local restrictions, remote expiry, screen lock and accessibility.

# OAuth compatibility — 2026-10-21

- Allow Claude's exact documented OAuth callback for automatic client registration.
- Identify the requesting client on consent and limit its form redirect policy
  to that client's validated callback origin.
- Preserve existing ChatGPT callbacks, PKCE, CSRF, code replay prevention, refresh
  rotation, secrets and phone rules. No APK update or database migration required.
- Regression tests exercise both clients through consent, token exchange and refresh,
  and reject lookalike/modified callbacks. Actual Claude account sign-in still
  requires verification in Claude after deployment.

# 1.3.0 productivity preview

- Added UTC one-time windows and structured weekly IANA-timezone recurrence,
  including overnight windows and explicit DST gap/overlap handling.
- Added schedule CRUD, preset CRUD/activation and timed/indefinite pause/resume;
  expanded MCP from four to fifteen tools without changing authentication.
- Added complete persisted v2 desired snapshots, validation/commit gates and
  per-revision scheduler readiness before acknowledgement.
- Added a device-local union evaluator, one next-transition alarm/timer, boot and
  clock-change restoration; removed the old one-second remote expiry poll.
- Expanded heartbeat/status with readiness, observations, upcoming/paused rules,
  next transition, clock skew and bounded errors; stale state is never confirmed active.
- Added minimal schedule/preset/pause UI while retaining background app discovery,
  select-all and existing saved credentials/permissions on an in-place update.
- Added an additive D1 migration and regression coverage for immediate blocks,
  calendar behavior, offline calculations, full-state persistence, auth, allowlist
  revocation, overlaps and upstream restriction precedence.
- Deferred optional push, grace periods, pre-start notifications and event history.
  Samsung Doze/reboot/overlay behavior still requires real-device acceptance testing.

