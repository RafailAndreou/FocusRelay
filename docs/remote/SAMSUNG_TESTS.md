> Historical development notes. For a new installation, follow [Cloudflare setup](../CLOUDFLARE_SETUP.md). Personal deployment instructions below are not setup requirements.

# Samsung SM-A356B manual acceptance checklist

Scheduled behavior is NOT yet verified on a physical device. Automated calendar,
parser, policy and widget tests do not prove Samsung alarm/service reliability.
Use harmless test apps and short windows first. Keep an independent way to open
Settings; protected phone controls must never appear in the remote allowlist.

1. Install the new signed 1.3.0 APK over Preview 2/3. Do not uninstall. Open Mindful;
   confirm saved URL/token and existing app selection remain. If Android reports a
   signature conflict, stop and capture the exact message; do not erase data.
2. In ChatGPT remote control, verify Connected, last sync and the local scheduler.
   Recheck Usage Access, display-over-other-apps, notifications and unrestricted
   battery use. Tap **Allow precise scheduled blocks**, grant Alarms & reminders,
   return to Mindful. A received future rule should show scheduler `exact`.
3. Confirm `list_apps` shows only opted-in apps. Select all/save if desired. Launcher,
   Settings, dialer, permission controllers and Mindful must remain excluded.
4. REGRESSION: block Coca-Cola for two minutes. Check desired revision becomes
   acknowledged, then launch the app and check overlay. Repeat with the app already
   open. Confirm it expires without extending after another sync.
5. FUTURE/OFFLINE: schedule a test app from now+3 minutes to now+5 minutes. Wait for
   acknowledgement and a displayed next transition. Enable airplane mode BEFORE
   start. Leave the app open: verify the block starts and ends locally; note actual
   times/delay. Reconnect and verify the original expiry did not restart.
6. OVERLAP: create A now+2 to +5 minutes, B now+3 to +7 on the same app. Delete A,
   wait for acknowledgement, and verify B still holds the app to +7. Repeat with an
   immediate timed block overlapping a schedule; `unblock_apps` must remove only
   the immediate block. Delete B separately to clear it.
7. RECURRENCE: create a weekly rule on today's ISO weekday starting a few minutes
   ahead. Verify one occurrence. Inspect Mon/Wed/Fri and weekday schedules using
   `list_schedules`; verify saved zone and local times.
8. OVERNIGHT: create a real 23:30–07:30 daily window, check next-morning ownership
   and expiry. Do not move the phone's clock for a daily-use acceptance test.
   DST gap/overlap conversion is covered by automated tests; verify the next actual
   DST night separately if this feature becomes important to regular use.
9. PRESET: create Study with allowed app(s) and default duration. Verify creation
   does not block. Activate for two minutes, wait for acknowledgement, check overlay
   and expiry. Activate twice with different ends; one ending must not release the
   other. Editing template defaults must not rewrite an existing activation.
10. PAUSE: pause a received schedule until a near-future absolute time; wait for
    acknowledgement, then go offline. Verify local resume at that timestamp and
    original end time. Test indefinite pause/resume from ChatGPT and from the app.
    Offline UI pause must show an error rather than falsely claiming success.
11. UPSTREAM: start an ordinary Mindful focus/bedtime/app-limit restriction, add a
    remote block on the same app, then pause/delete/unblock the remote rule. The
    ordinary Mindful restriction must remain. Test cached app limits too.
12. REVOCATION: remove an app from App access while remotely blocked; save. It must
    stop being remotely enforced locally and disappear from new relay definitions.
    Re-adding opt-in must not reactivate removed package references automatically.
13. PERMISSIONS: revoke overlay or Usage Access. After heartbeat, get_status must
    not show confirmed active blocks; acknowledgement should stop. Restore access,
    reopen Mindful, sync, and retest. Never infer enforcement from accepted=true.
14. LOCK/DOZE: receive a future schedule, lock the screen, and test transitions after
    at least 15–30 minutes of idle time, on charger and off charger. Record alarm
    delay, scheduler mode, notification visibility and tracker readiness.
15. REBOOT/OFFLINE: receive a schedule, go offline and reboot. Unlock the phone;
    verify local restoration before its start without requiring relay access.
    Try a reboot during an active window and after its end. Record any Samsung
    restriction requiring a manual reopen. No promise is made before first unlock.
16. INEXACT MODE: temporarily revoke Alarms & reminders access. Reopen Mindful and
    verify the honest inexact warning. Check it neither crashes nor claims precise
    timing. Restore access and verify the next rule shows exact mode.
17. FORCE-STOP: force-stop Mindful, observe that Android can cancel alarms and stop
    enforcement. Reopen it; verify current state/next transition is recomputed.
    This is a documented limitation, not an expected tamper-proof guarantee.
18. DISCONNECT: disconnect and confirm remote rules/alarms/credentials clear while
    ordinary Mindful restrictions remain. Relay rules remain remotely stored until
    deleted/expired, so reconnecting may restore them.

For failures record: APK version, exact clock time/zone, app package, schedule id,
desired/applied revisions, last sync, scheduler mode/next transition, permission
state, airplane/lock/battery state, and the error shown. Do not include credentials.

