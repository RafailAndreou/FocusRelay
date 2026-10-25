// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
// SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.remote

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class RemoteSnapshotParserTest {
    private val app="com.instagram.android"
    private fun envelope()=JSONObject("""{"schemaVersion":2,"revision":8,"serverTimeMs":1000,"blocks":[],"presets":[],"schedules":[{"id":"rule-a","name":"Study","packages":["com.instagram.android"],"pausedUntilMs":0,"timing":{"type":"once","startAtMs":2000,"endAtMs":4000}}]}""")
    @Test fun completeSnapshotRoundTripsAndHasNoNetworkDependency() {
        val original=envelope();val saved=JSONObject(original.toString())
        val snapshot=RemoteSnapshotParser.parse(saved,setOf(app))
        assertEquals(8L,snapshot.revision)
        assertEquals(4000L,RemoteRuleEngine.evaluate(snapshot,setOf(app),3000).activeBlocks[app])
    }
    @Test fun oneInvalidRuleRejectsWholeEnvelopeWithoutPartialResult() {
        val json=envelope();val invalid=JSONObject(json.getJSONArray("schedules").getJSONObject(0).toString()).put("id","bad")
        invalid.getJSONObject("timing").put("endAtMs",1000);json.getJSONArray("schedules").put(invalid)
        assertThrows(IllegalArgumentException::class.java){RemoteSnapshotParser.parse(json,setOf(app))}
    }
    @Test fun unknownOrRevokedAppsRejectNewDesiredState() {
        assertThrows(IllegalArgumentException::class.java){RemoteSnapshotParser.parse(envelope(),emptySet())}
        val json=envelope();json.getJSONArray("schedules").getJSONObject(0).put("packages",JSONArray().put("com.invalid;code"))
        assertThrows(IllegalArgumentException::class.java){RemoteSnapshotParser.parse(json,setOf("com.invalid;code"))}
    }
    @Test fun duplicateIdsWrongTypesAndUnknownParentsRejectWholeSnapshot() {
        val duplicate=envelope();duplicate.getJSONArray("schedules").put(duplicate.getJSONArray("schedules").getJSONObject(0))
        assertThrows(IllegalArgumentException::class.java){RemoteSnapshotParser.parse(duplicate,setOf(app))}
        assertThrows(IllegalArgumentException::class.java){RemoteSnapshotParser.parse(envelope().put("revision","8"),setOf(app))}
        val parent=envelope();parent.getJSONArray("schedules").getJSONObject(0).put("presetId","missing")
        assertThrows(IllegalArgumentException::class.java){RemoteSnapshotParser.parse(parent,setOf(app))}
    }
    @Test fun schemaDowngradeCannotEraseAlreadyReceivedSchedules() {
        val legacy=JSONObject("""{"revision":8,"serverTimeMs":1000,"blocks":[]}""")
        assertThrows(IllegalArgumentException::class.java){RemoteSnapshotParser.parse(legacy,setOf(app),requireV2=true)}
        assertEquals(8L,RemoteSnapshotParser.parse(legacy,setOf(app)).revision)
    }
    @Test fun disabledEmptyRuleIsPersistedButNeverEnforced() {
        val json=envelope();json.getJSONArray("schedules").getJSONObject(0).put("packages",JSONArray()).put("disabled",true)
        val state=RemoteSnapshotParser.parse(json,emptySet())
        assertEquals("unavailable",RemoteRuleEngine.evaluate(state,emptySet(),3000).states.single().phase)
    }    @Test fun acknowledgementCandidateExistsOnlyAfterSuccessfulWholeSnapshotCommit() {
        var persisted=false
        val valid=envelope()
        val accepted=RemoteSnapshotParser.validateAndPersist(valid,setOf(app),7,true) { persisted=true; true }
        assertTrue(persisted);assertEquals(8L,accepted.revision)
        assertThrows(IllegalStateException::class.java) { RemoteSnapshotParser.validateAndPersist(valid,setOf(app),7,true) { false } }
        persisted=false
        val bad=envelope();bad.getJSONArray("schedules").getJSONObject(0).getJSONObject("timing").put("endAtMs",1)
        assertThrows(IllegalArgumentException::class.java) { RemoteSnapshotParser.validateAndPersist(bad,setOf(app),7,true) { persisted=true;true } }
        assertFalse(persisted)
        assertThrows(IllegalArgumentException::class.java) { RemoteSnapshotParser.validateAndPersist(valid,setOf(app),9,true) { persisted=true;true } }
        assertFalse(persisted)
    }

}

