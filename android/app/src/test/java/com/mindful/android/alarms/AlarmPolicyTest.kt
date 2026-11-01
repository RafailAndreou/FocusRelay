// Copyright 2026 Rafail Andreou. SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.alarms

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import org.json.JSONArray
import java.time.Instant
import java.time.ZoneId

class AlarmPolicyTest {
    private fun ms(s: String)=Instant.parse(s).toEpochMilli()
    private val zone=ZoneId.of("Asia/Nicosia")
    private fun alarm()=JSONObject().put("id","alarm-test").put("triggerAtMs",ms("2026-10-31T11:00:00Z")).put("label","Wake up")
        .put("skipUi",false).put("createdAt",ms("2026-10-31T09:00:00Z")).put("updatedAt",ms("2026-10-31T09:00:00Z")).put("daysOfWeek",JSONArray())
    @Test fun convertsToPhoneZone(){assertEquals(13 to 0,AlarmPolicy.wallTime(ms("2026-10-31T11:00:00Z"),ms("2026-10-31T09:00:00Z"),zone))}
    @Test fun tomorrowMorning(){assertEquals(7 to 30,AlarmPolicy.wallTime(ms("2026-11-01T05:30:00Z"),ms("2026-10-31T20:00:00Z"),zone))}
    @Test fun metadataSurvivesSerializationAndReboot(){val a=alarm().put("associatedActivationId","sleep");val restored=JSONArray(JSONArray().put(a).toString());AlarmPolicy.validateAll(restored);assertEquals("sleep",restored.getJSONObject(0).getString("associatedActivationId"))}
    @Test fun expiredRejected(){assertThrows(IllegalArgumentException::class.java){AlarmPolicy.wallTime(60000,120000,zone)}}
    @Test fun beyond24HoursRejected(){assertThrows(IllegalArgumentException::class.java){AlarmPolicy.wallTime(172800000,0,zone)}}
    @Test fun dstFoldIsAmbiguous(){assertThrows(IllegalArgumentException::class.java){AlarmPolicy.wallTime(ms("2026-10-25T01:30:00Z"),ms("2026-10-24T20:00:00Z"),zone)}}
    @Test fun dstSpringNormalTime(){assertEquals(7 to 30,AlarmPolicy.wallTime(ms("2026-03-29T04:30:00Z"),ms("2026-03-28T20:00:00Z"),zone))}
    @Test fun cancelUnlaunchedRequest(){assertEquals("cancelled",AlarmPolicy.cancel(false))}
    @Test fun systemCancellationNotFaked(){assertEquals("user_action_required",AlarmPolicy.cancel(true))}
    @Test fun duplicateIdsRejected(){assertThrows(IllegalArgumentException::class.java){AlarmPolicy.validateAll(JSONArray().put(alarm()).put(alarm()))}}
    @Test fun recurringUnsupported(){assertThrows(IllegalArgumentException::class.java){AlarmPolicy.validate(alarm().put("daysOfWeek",JSONArray().put(1)))}}
    @Test fun oldSnapshotsNeedNoAlarmMigration(){AlarmPolicy.validateAll(JSONArray())}
    @Test fun missingHandlerDoesNotLaunchOrMarkAttempted(){
        val a=alarm();var launched=false
        assertThrows(IllegalArgumentException::class.java){AlarmHandoff.perform(a,{1},{},{false},{launched=true})}
        assertFalse(launched);assertFalse(a.optBoolean("launchAttempted"))
    }
    @Test fun persistenceFailurePreventsExternalSideEffect(){
        var launched=false
        assertThrows(IllegalStateException::class.java){AlarmHandoff.perform(alarm(),{1},{error("disk full")},{true},{launched=true})}
        assertFalse(launched)
    }
    @Test fun crashDuringHandoffPersistsUncertaintyAndNeverReplays(){
        val a=alarm();var persisted="";var launches=0
        assertThrows(AssertionError::class.java){AlarmHandoff.perform(a,{1},{persisted=a.toString()},{true},{launches++;throw AssertionError("process died")})}
        val restored=JSONObject(persisted)
        assertEquals("user_action_required",restored.getString("state"))
        assertThrows(IllegalArgumentException::class.java){AlarmHandoff.perform(restored,{2},{},{true},{launches++})}
        assertEquals(1,launches)
    }
    @Test fun successfulHandoffIsLaunchedNotSystemConfirmed(){
        val a=alarm();AlarmHandoff.perform(a,{1},{},{true},{})
        assertEquals("launched",a.getString("state"));assertFalse(a.optBoolean("systemConfirmed"))
    }
    @Test fun cancellationBlocksLaunch(){
        assertThrows(IllegalArgumentException::class.java){AlarmHandoff.perform(alarm().put("cancelRequestedAt",1),{1},{},{true},{fail("Must not launch")})}
    }
    @Test fun snapshotExtensionIsValidatedBeforeRevisionAcknowledgement(){
        val modes=JSONObject().put("schemaVersion",1).put("templates",JSONArray()).put("instances",JSONArray()).put("exceptions",JSONArray()).put("alarms",JSONArray().put(alarm()))
        com.mindful.android.modes.DeviceModeParser.parse(modes,emptySet())
        modes.getJSONArray("alarms").getJSONObject(0).put("label","")
        assertThrows(IllegalArgumentException::class.java){com.mindful.android.modes.DeviceModeParser.parse(modes,emptySet())}
    }
}
