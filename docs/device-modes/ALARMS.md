# System alarm requests (V1)

FocusRelay delegates ringing, snooze, sound, vibration and alarm screens to Android's Clock app. It never plays an alarm itself. Only FocusRelay-requested metadata is listed; it cannot inspect arbitrary system alarms.

## Assistant tools

- `set_alarm({triggerAt, requestId, label?, skipUi?})`: absolute ISO timestamp with explicit offset, minute precision, next 24 hours. Use a stable requestId (1–64 letters/numbers/_/-) on retries. Omit skipUi for the normal Clock UI.
- `list_remote_alarms({})`: desired metadata, latest local observation, freshness, desired/applied revisions, next future request. An offline/stale observation does not confirm handoff.
- `cancel_alarm({id})`: the phone cancels an unlaunched request after synchronization. If handoff was attempted, returns/observes `user_action_required`; open Clock and remove the labelled alarm there. Unknown ids return a tool error (`not_found`).

Examples for the assistant:

“Set an alarm for 13:00.” Resolve the date and explicit timezone first, then call set_alarm. “Set an alarm for 7:30 tomorrow” uses tomorrow's correct UTC offset, not today's offset.

“Start Sleep mode until 13:00 and set a wake-up alarm.” Activate the template with `until` and `alarm:{enabled:true,label:"Wake up"}`. An enabled alarm with no triggerAt uses the one-time mode end rounded up to a minute. Template/activation `alarm:{enabled:true,triggerAt:"2026-10-31T13:00:00+02:00",label:"Wake up"}` is also supported. Legacy alarmTime remains accepted and creates tracked metadata. Each activation snapshots its own request. Stopping/pausing a mode does not cancel an alarm; cancel it explicitly.

“Cancel my wake-up alarm.” List tracked alarms, resolve its id, then cancel_alarm. Never say a system Clock alarm was cancelled merely because the request was accepted.

## Local setup and handoff

Enable Device Modes, device settings actions, and the separate **Allow system alarm requests** switch on the phone. Remote callers cannot grant this consent. Under Modes, requests show their time, label, state and **Open Clock**. Tap it while FocusRelay is visible. This avoids Android background-activity restrictions. Requests are **not automatically launched** when synced or a mode starts. Do not rely on a requested wake-up alarm until you have checked it in Clock.

The editor has a wake-up toggle, optional time picker and label. A specific picked timestamp is a one-time value, not a daily template default; an expired saved timestamp must be changed. Use mode-end defaults for reusable one-time modes.

`com.android.alarm.permission.SET_ALARM` was already declared and is retained. Handoff uses official `AlarmClock.ACTION_SET_ALARM`, EXTRA_HOUR/MINUTES/MESSAGE/SKIP_UI. skipUi is a request the OEM may ignore; successful startActivity only means **launched**, never confirmed creation. FocusRelay adds a short FR id suffix to labels for identification. It does not mark ringing/dismissal as observed. After the requested time, metadata is `expired` (time passed, not proof of ringing).

Cancellation of a launched alarm uses a user-opened `ACTION_SHOW_ALARMS` management screen. ACTION_DISMISS_ALARM has OEM-dependent search/matching semantics and no reliable receipt or stable alarm id, so V1 does not silently target or claim to cancel one. The app does not mark it cancelled after returning from Clock. Change an unlaunched request by cancelling it and setting a new requestId; change a launched one in Clock.

## Time and OEM limitations

ACTION_SET_ALARM has no date field. Immediately before launching, the phone uses its current timezone to verify that the requested absolute timestamp is the **next local occurrence** of the supplied hour/minute. Expired values, values beyond 24 hours, a different date or a DST fold/gap are rejected. Clock apps remain OEM-specific: inspect the actual date/time there, particularly around DST. Recurring alarms and wall-clock-only MCP parameters are explicitly unsupported in V1; use absolute one-time requests. Scheduled weekly modes with alarm actions are rejected instead of guessing which occurrence should ring.

Samsung Clock, Google Clock, AOSP Clock and every installed SET_ALARM/SHOW_ALARMS handler are protected alongside emergency/system apps. These guards also apply to remote allow-list sessions and the final overlay check. Clock package changes may require restarting the app to refresh caches.

## Persistence, revisions and recovery

No SQL migration or destructive schema bump. `assistant_state` schema1 receives an optional bounded `alarms` array; old rows without it mean empty. Existing schedules/presets and envelope3 remain unchanged. New native parsing validates the extension before desired-state persistence/acknowledgement. Relay rejects alarm mutations unless the phone advertises alarmSupportVersion1 and local consent. Metadata is bounded at 32, with relay pruning after seven days; phone metadata lives in a separate private `focusrelay_alarms_v1` preference journal and survives reboot. This is not a system alarm database.

Fields: id, triggerAtMs, label, daysOfWeek (empty V1), skipUi, createdAt/updatedAt, state, optional associatedModeId/associatedActivationId, cancelRequestedAt. Local state also records launchAttempted/cancelHandledAt. Desired-state acknowledgement means persisted, not created in Clock. Status uses `requested`, `user_action_required`, `launched`, `cancelled` (unlaunched request only), `failed`, `expired`; systemConfirmed is always false.

The phone journals an uncertain handoff **before** startActivity. Re-delivery, reboot or process death never automatically retries that side effect. If interrupted, inspect Clock before creating another request. Missing handlers and launch refusal become structured local failures. Disabling consent prevents new handoffs; it cannot undo alarms already delegated to Clock. Metadata is retained for manual recovery even if an older relay omits the extension.

## Samsung manual checks

1. Update in place; verify old blocks, schedules, presets, pairing and consent survived.
2. Enable local alarm consent/actions; sync, call set_alarm for 3 minutes ahead. Before Open Clock, confirm MCP says user action required, not system confirmed.
3. Tap Open Clock, verify Samsung Clock time/label/date and save if asked. Return: state launched, systemConfirmed false. Verify actual ringing separately.
4. Repeat with skipUi true: check whether Samsung honors it; never infer confirmation from the flag.
5. Cancel an unlaunched request, sync and confirm cancelled. Cancel a launched request, confirm user_action_required, open Clock and delete it manually; confirm no silent success claim.
6. Activate a one-time Sleep mode with an end alarm. Under allow-list restrictions, open Clock and verify alarm screen remains accessible, including while the blocking overlay was visible.
7. Re-send the same requestId; confirm one metadata record and no second handoff. Force-stop/reboot before and after handoff, then sync; no automatic replay.
8. Disable alarm consent/actions, disconnect internet, and check stale status. Existing Clock alarms remain independent; queued requests cannot be called created.
9. Test missing/disabled Clock handler, expired timestamp, timezone change and DST boundary in a test device/emulator. Expect a clear failure or ambiguity rejection.
10. Check normal local rules and remote schedule enforcement still work after returning from Clock.

## Future option (not implemented)

Mindful-owned `AlarmManager.setAlarmClock()` could provide stable pending intents and owned cancellation, but would require an actual reliable alarm execution path, reboot rescheduling and additional UX. Prefer checking/requesting SCHEDULE_EXACT_ALARM where appropriate rather than adding USE_EXACT_ALARM to an app whose primary purpose is blocking. Existing exact-alarm permission is used by the blocking scheduler; V1 adds no exact-alarm playback or new full-screen/wake-lock feature.
