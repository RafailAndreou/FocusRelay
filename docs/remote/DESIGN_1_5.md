> Historical development notes. For a new installation, follow [Cloudflare setup](../CLOUDFLARE_SETUP.md). Personal deployment instructions below are not setup requirements.

# Design Preview 1.5.0

This release implements the accepted UX plan on top of 1.4.0. It keeps the
`com.mindful.android.remote.design` package and uses version code 150. The delivered
APK is signed with the saved Design Preview certificate, not the CI debug key.
No relay schema, deployed Worker, live blocks or owner credentials are changed.

## Everyday flows

- Neutral charcoal surfaces, soft mint actions and lavender/amber supporting
  accents replace the green-tinted surfaces. Light, system, dynamic colour,
  AMOLED and accent preferences now apply to the main tabs and rule editors too.
  The historical default `Indigo` preference is presented as `Mint (default)`.
- Tapping the Quick Block card opens apps immediately. Timer changes duration
  separately; Start and Stop have their own targets. Configuring Quick Block
  affects its next start and does not rewrite an already running block.
- Existing rules open with their current app selection. Their Schedule, Timer or
  Daily allowance control opens the condition. Back warns about unsaved changes.
  New rules still follow type choice -> timing -> apps -> review.
- Real package icon bytes already supplied by Android are reused in Quick Block,
  rules, timeline entries, allowance cards and blocking history. Groups show one
  or two icons according to available width and the exact hidden +N count.
  Removed packages and unavailable/invalid icons have labelled fallbacks. App
  discovery remains separate from status polling.
- Shared ripples, short transitions, sheets and dialogs replace elastic page
  flights. New navigation/editor/permission flows and shared list/hero/panel
  effects respect reduced motion. Existing animated charts and live countdowns
  retain their original implementations.

## Screen and route coverage

The migration replaces presentation through the common feature shell, rows,
selection sheets, dialogs and theme while retaining existing providers, charts,
validation and restriction logic. It does not duplicate those features into a
second database or migrate active restrictions.

| Area | Custom destination / presentation | Retained functions |
| --- | --- | --- |
| Android launch and Flutter startup | Neutral launch background/leaf mark, compact loading/auth page | Initialization, protected access and initial links |
| Setup / permission recovery | Welcome and permission checklist, compact grant explanations | Required grants, OS settings, return/resume status |
| Usage and individual apps | Activity -> Detailed usage statistics; Settings -> App restrictions | Date/history charts, screen/network use, app limits, exclusions, emergency/protection gates |
| Focus / active session / history | Settings -> Focus sessions | Profiles, app selection, duration, DND, start/finish, reflections and history |
| Bedtime | Settings -> Bedtime | Times, days, selected apps, DND and protection |
| Original app groups | Settings -> App groups | Group creation/editing, shared limits and active periods |
| Websites and Shorts | Settings -> Websites / Short videos | Existing lists, limits, accessibility/VPN grants |
| Notifications | Settings -> Notifications | Batching, schedules, stored notifications, search and conversations |
| Parental / tamper controls | Settings -> Protection | Protected access, invincible mode and existing policy gates |
| Remote control | Settings -> Remote control | Pairing, allowed apps, schedules, presets, pause/resume and sync status; diagnostics are expandable, scheduling caveats stay visible |
| Preferences / database / About | Settings -> Advanced settings | Appearance, locale, default tab, history retention, backup/import, crash logs, attribution and support links |
| About / licenses / changes | Custom About and What's new | Original authorship, bundled GPL-2.0 text, dependency licenses and upstream release history |
| Confirmation, inputs, timers and pickers | Shared neutral dialogs/choice sheets | Original validation, return values, protection countdowns and cancellation |

The former `/originalHome` route is a compatibility redirect to focused pages;
it cannot reopen the old dashboard. Supported notification and app-detail links
use the same custom shell. Actual Android settings, biometric and file-picker
surfaces remain OS-owned.

The decorative country slogan is removed from startup, the shared footer and
native blocker. The original copyright headers and GPL license remain intact.

## Native blocker and reminder

The blocker replaces quotes and the old bottom sheet with app icon/name, a clear
heading, restriction reason, applicable timing and Back to Home. Daily allowances
state their midnight reset; other local/remote blocks show their actual end time.
The existing emergency-management action remains available only for a genuinely
exhausted positive budget, and its app details retain their protection gates.
There is no new unrestricted bypass for protected rules.

Window registration, terminal disposal, queued-request invalidation, own-app
exclusion, immediate revocation and one-shot expiry cleanup are preserved.
System-bar inset and non-focusable flags are unchanged. Appearance animations
never delay enforcement or removal. Toast reminders now also own and clean up
one WindowManager registration and reject callbacks after disposal.

## Data and update limits

Local rule/Quick Block preferences, app restrictions, history and remote pairing
use the same existing storage. Updating the matching package/certificate is
intended to retain them; actual installation and retention must be checked on the
phone. Database exports continue to cover the original Mindful database only.
Local rules, Quick Block configuration, blocking events and remote pairing are
not part of that export; the Database page explains this explicitly.

## Verification

Build and signature results are recorded after the cloud run. Widget screenshots
are rendered from the implemented Flutter widgets with isolated test data; their
fallback initial icons are not evidence of installed brand icons on a phone.

Physical Samsung acceptance remains pending: install over Design Preview, check
saved rules/pairing, inspect actual app icons and +N, then perform short local and
remote blocks while checking Home, Recents, switching apps, expiry and revocation.
These checks should precede treating the revised native UI as on-device verified.

### Final verified build

- Source: `89974546932d2b5410b2a39b0fcddbaced6ec41e`.
- [Successful cloud run](https://github.com/RafailAndreou/FocusRelay/actions/runs/39717568315): Flutter analysis passed; 22 Flutter tests (including 12-page feature audit), 56 native JUnit tests and 35 relay tests passed with no failures. Relay deploy dry-run also passed.
- APK artifact `12400570358`; UI artifact `12398849695` contains 20 rendered screens.
- Delivered APK: `Mindful_Design_1_5_0.apk`, 98052931 bytes.
- Package/version independently parsed: `com.mindful.android.remote.design`, 1.5.0/code 150.
- SHA-256: `2817cbeebb1e88ad7ff8f01d1f087f0455e3d7ec4387d724a84a882d98a9fa15`.
- Certificate SHA-256: `0316f9855e3ed2545a7ffbb84d3f3145705060b0e7c75a0556823640af2aebe1`; matches the delivered 1.4.0 Design Preview certificate. APK v2/v3 signatures verified; five uncompressed native libraries retain 16 KiB ZIP alignment.
- The matching package/certificate/version were verified here. Actual installation, data retention and Samsung overlay/navigation behavior remain phone acceptance work.

## 1.5.1 rule-switch patch

The paused thumb previously inherited the same colour as its track. Explicit
state colours now keep the thumb visible in light, dark and AMOLED themes,
including while its save is pending. Thumb/track contrast is checked at 3:1.

Rule toggles now track pending values by rule ID rather than setting the shared
busy flag. Only the switch being saved is temporarily unavailable. Other rules
can be toggled concurrently, and keyed rows retain their switch elements during
refreshes and filter changes. A requested value is held through any in-flight
poll and a subsequent state fetch, so an older poll cannot reverse its animation.
A failed write restores the saved value and reports the error.

The regression checks cover contrast, independent concurrent saves with stable
unrelated switch elements, and failure rollback. The paused-rules screenshot is
rendered from the actual widgets with isolated fixture data.

### Verified 1.5.1 build

- Source: `dfc4187d22337b6e962fab3427e11653036e7018`.
- [Successful cloud run](https://github.com/RafailAndreou/FocusRelay/actions/runs/39787634424): analysis passed; 25 Flutter, 56 native JUnit and 35 relay tests passed (116 total). Relay deployment dry-run passed.
- APK artifact `12429054973`; UI artifact `12429314614` contains 21 rendered screens.
- Delivered package `com.mindful.android.remote.design`, version `1.5.1`, code `151`.
- Saved Design Preview certificate verified against APK v2/v3 signatures; all five uncompressed native libraries remain 16 KiB aligned.
- APK SHA-256: `6ebb0c9fce206b374dedd1f4384804205289668239ba860db7984ee3b7eb0248`.
- Install over 1.5.0 using the matching package/certificate. Real-phone switch animation and saved-data retention still require device verification.

