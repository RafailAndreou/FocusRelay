# Automatic remote alarms (1.7.3)

New alarms default to `delivery: "focusrelay"`. After the phone receives and persists the desired state, it calls official `AlarmManager.setAlarmClock()` using a stable, immutable PendingIntent. There is no per-alarm Clock handoff. At the deadline a private receiver starts the mediaPlayback foreground service, which plays the configured Android alarm sound on the alarm audio stream. A visible notification and the Modes screen provide Dismiss. Ringing stops after ten minutes. No custom snooze or full-screen alarm activity is included.

## One-time phone setup

Install 1.7.3, connect remote control and enable Device Modes. In Modes, open **Alarm and notification permissions**, grant Alarms & reminders and allow notifications. Set a nonzero alarm volume in Android sound settings. Then enable **Allow automatic FocusRelay alarms**. This explicit consent is separate from optional DND/brightness/volume actions and from Clock handoff consent. Remote commands cannot grant it.

The app retains SCHEDULE_EXACT_ALARM and uses canScheduleExactAlarms(), not USE_EXACT_ALARM. It adds FOREGROUND_SERVICE_MEDIA_PLAYBACK, with a private mediaPlayback service. No full-screen intent permission is added. Missing exact-alarm access, blocked notification channel or muted alarm volume produce explicit readiness errors. The app never raises the user's volume or bypasses DND. Muting alarms or changing DND after scheduling can silence them.

## MCP

- `set_alarm({triggerAt, requestId, label?, delivery?})` defaults to focusrelay. Use an explicit offset-qualified absolute timestamp to minute precision within 24 hours. Stable requestId retries deduplicate. Example: “Set an alarm for 7:30 tomorrow.” Resolve tomorrow's correct local offset first.
- `activate_device_mode({id, until, alarm:{enabled:true,label:"Wake up"}})` snapshots a one-time wake-up alarm at the mode end, rounded up to a minute. Example: “Start Sleep mode until 13:00 and set a wake-up alarm.” Explicit alarm.triggerAt is also supported.
- `cancel_alarm({id})` cancels the owned PendingIntent and stops it if ringing once the phone syncs. Offline cancellation remains pending. List first to resolve “Cancel my wake-up alarm.”
- `list_remote_alarms` / get_status expose desired/applied revisions, metadata, local observation and freshness. `requested` is not scheduled. Fresh acknowledged `scheduled` means AlarmManager accepted registration; `ringing` means local playback started. Neither is proof the person heard it. Cancellation is confirmed only by a phone report. Other states: dismissed, missed, failed, cancelled, user_action_required.

After relay deployment, refresh/reconnect MCP to discover updated parameters and new tools. No alarms are created during deployment.

## Persistence and recovery

Old alarm metadata without delivery remains `system_clock`, with its original unverified foreground handoff. The app never silently adopts or duplicates a Clock alarm. New owned records live in separate private preferences and are independent of app restrictions. No SQL migration or reset; envelope3 remains additive. Capability version2 and explicit automaticAlarmConsent/automaticAlarmReady are required for remote automatic requests. Old phones cannot accept these commands.

Future alarms are re-registered after normal unlocked boot, package replacement, time/timezone or exact-permission grant broadcasts, and normal synchronization. Absolute timestamps do not shift with a timezone change. Dismissed/cancelled tombstones prevent replay. Overdue alarms are missed rather than ringing unexpectedly after reboot. Stable PendingIntent identity prevents duplicate alarm registrations. Settings/permission changes can invalidate Android registrations and appear as failures at the next check. Turning automatic alarms off cancels owned alarms and stops playback; re-enabling does not resurrect cancelled alarms. Stopping a mode does not cancel its wake-up alarm. Disconnecting remote control does not remove an already scheduled wake-up alarm; manage it locally.

Only one sound plays at once; a later overlapping alarm replaces/dismisses the earlier sound with an explicit reason. Audio-focus interruption (for example a call) stops playback and records failure. There is no automatic retry of a sound after process death. Android's force-stop prevents receivers until reopening; a powered-off phone cannot ring. Samsung sleeping-app settings, notification delivery, DND, hardware audio routing and OS behavior need physical-device acceptance tests. Keep a known-working Clock alarm as backup during testing.

## Samsung test checklist

1. Install over the previous release. Confirm pairing, old rules and schedules persist. Enable automatic alarm consent and permissions; verify zero/muted alarm volume blocks setup.
2. Ask for an alarm 3–5 minutes ahead. Wait for status scheduled and matching appliedRevision, then close/lock the phone. Verify actual ringing and lockscreen notification Dismiss.
3. Set a Sleep mode with an alarm while allow-list restrictions are active. Verify ringing, FocusRelay access and Clock/emergency access remain available.
4. Cancel a scheduled alarm remotely and wait for cancelled phone observation. Verify it does not ring. Cancel a ringing one and verify audio stops; repeat local Dismiss with internet off.
5. Re-send one requestId and sync repeatedly; verify only one ring. Schedule two close alarms and check replacement and dismiss behavior.
6. Reboot before a future alarm, unlock once and disconnect internet; verify it still rings. Reboot after the deadline: verify missed, without replay.
7. Revoke exact access, disable notifications/channel, change timezone and mute alarm volume. Verify honest errors; no false claim of audible ringing. Test while DND is on and during a call.
8. Force-stop and reopen. Verify cancelled/dismissed alarms do not return. Turn automatic alarm consent off and verify scheduled/ringing owned alarms stop, while an old Clock handoff is unchanged.
9. Check normal local/remote blocks, temporary exceptions and schedules still work after an alarm.

References: [Android exact alarms](https://developer.android.com/develop/background-work/services/alarms), [AlarmManager](https://developer.android.com/reference/android/app/AlarmManager), [foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types), [audio focus](https://developer.android.com/media/optimize/audio-focus).
