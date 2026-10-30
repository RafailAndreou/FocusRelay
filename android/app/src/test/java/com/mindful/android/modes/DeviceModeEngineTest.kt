// Copyright 2026 Rafail Andreou. SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.modes

import com.mindful.android.remote.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DeviceModeEngineTest {
    private val now=1793354400000L
    private val eligible=setOf("com.example.chat","com.example.video","com.example.mail")
    private fun mode(id: String="a", allowed: Set<String> = setOf("com.example.chat"), kind: String="allow_list", blocked: Set<String> = emptySet(), start: Long=now, end: Long=now+3600000, actions: Map<String,Int> = emptyMap())=DeviceModeInstance(id,"template","Work",kind,allowed,blocked,emptySet(),RemoteTiming.Once(start,end),end,now,actions)
    @Test fun allowListIsDynamicAndCannotReachNonOptedApps(){
        val s=DeviceModeState(listOf(mode()));val e=DeviceModeEngine.evaluate(s,eligible,now)
        assertEquals(setOf("com.example.video","com.example.mail"),e.blocks.keys)
        assertEquals(setOf("com.new.app"),DeviceModeEngine.evaluate(s,setOf("com.example.chat","com.new.app"),now).blocks.keys)
        assertFalse(e.blocks.containsKey("com.android.settings"))
    }
    @Test fun blockListRespectsEligibilityAndExplicitExclusions(){
        val m=mode(kind="block_list",allowed=emptySet(),blocked=setOf("com.example.video","com.android.settings")).copy(excludedPackages=setOf("com.example.video"))
        assertTrue(DeviceModeEngine.evaluate(DeviceModeState(listOf(m)),eligible,now).blocks.isEmpty())
    }
    @Test fun exceptionExpiresAndNeverOverridesIndependentMode(){
        val a=mode();val b=mode("b");val x=TemporaryException("ex","a","com.example.video",now,now+600000)
        val s=DeviceModeState(listOf(a,b),listOf(x));assertTrue(DeviceModeEngine.evaluate(s,eligible,now+1).blocks.containsKey(x.packageName))
        val onlyA=s.copy(instances=listOf(a));assertFalse(DeviceModeEngine.evaluate(onlyA,eligible,now+1).blocks.containsKey(x.packageName))
        assertTrue(DeviceModeEngine.evaluate(onlyA,eligible,now+600000).blocks.containsKey(x.packageName));assertEquals(now+600000,DeviceModeEngine.evaluate(onlyA,eligible,now+1).nextAtMs)
    }
    @Test fun sequentialWindowsAndExpiredRecoveryAreDeterministic(){
        val a=mode(end=now+600000);val b=mode("b",start=now+600000,end=now+1200000)
        val persisted=DeviceModeState(listOf(a,b));assertEquals(listOf("expired","active"),DeviceModeEngine.evaluate(persisted,eligible,now+600000).states.map{it.phase})
        assertTrue(DeviceModeEngine.evaluate(persisted,eligible,now+1200000).blocks.isEmpty())
    }
    @Test fun localStopAndConsentWinOverRedeliveredRemoteState(){
        val s=DeviceModeState(listOf(mode()));assertTrue(DeviceModeEngine.evaluate(s,eligible,now,stopped=setOf("a")).blocks.isEmpty())
        assertEquals("disabled",DeviceModeEngine.evaluate(s,eligible,now,enabled=false).states.single().phase)
    }
    @Test fun overlappingActionsChooseRestrictiveValuesAndEndingOneKeepsOtherOwners(){
        val a=mode(actions=mapOf("dnd" to 1,"brightness" to 30,"mediaVolume" to 10,"ringtoneVolume" to 40));val b=mode("b",actions=mapOf("dnd" to 1,"brightness" to 50,"mediaVolume" to 20,"ringtoneVolume" to 70))
        val s=DeviceModeState(listOf(a,b));val e=DeviceModeEngine.evaluate(s,eligible,now)
        assertEquals(a.actions,DeviceModeEngine.settingsRequests(e,true));assertTrue(DeviceModeEngine.settingsRequests(e,false).isEmpty())
        assertEquals(b.actions,DeviceModeEngine.settingsRequests(DeviceModeEngine.evaluate(s,eligible,now,stopped=setOf("a")),true))
    }
    @Test fun modeRestrictionsCannotReleaseLegacyImmediateOrScheduledBlocks(){
        val mode=mode();val exception=TemporaryException("ex","a","com.example.video",now,now+600000)
        val snapshot=RemoteSnapshot(4,mapOf("com.example.video" to now+900000),emptyList(),emptyList(),DeviceModeState(listOf(mode),listOf(exception)))
        assertEquals(now+900000,RemoteRuleEngine.evaluate(snapshot,eligible,now).activeBlocks["com.example.video"])
    }
    private fun snapshot(): JSONObject=JSONObject("""{"revision":3,"serverTimeMs":$now,"schemaVersion":3,"blocks":[],"presets":[],"schedules":[],"deviceModes":{"schemaVersion":1,"templates":[],"instances":[{"id":"a","templateId":"t","name":"Work","icon":"focus","description":"","mode":"allow_list","allowedPackages":["com.example.chat"],"blockedPackages":[],"excludedPackages":[],"durationMinutes":60,"timing":{"type":"once","startAtMs":$now,"endAtMs":${now+3600000}},"createdAtMs":$now,"expiresAtMs":${now+3600000},"pausedUntilMs":0}],"exceptions":[]}}""")
    @Test fun schemaThreePersistsFullModesBeforeAcknowledgement(){
        val json=snapshot();var called=false
        val s=RemoteSnapshotParser.validateAndPersist(json,eligible,2,true){called=true;it.deviceModes.instances.size==1}
        assertTrue(called);assertEquals(3L,s.revision);assertEquals("a",s.deviceModes.instances.single().id)
    }
    @Test fun malformedOrEmptyAllowListCannotReachPersistence(){
        val json=snapshot();json.getJSONObject("deviceModes").getJSONArray("instances").getJSONObject(0).put("allowedPackages",org.json.JSONArray())
        var persisted=false;assertThrows(IllegalArgumentException::class.java){RemoteSnapshotParser.validateAndPersist(json,eligible,2,true){persisted=true;true}};assertFalse(persisted)
    }
    @Test fun malformedDurationAndUnsafeActionsAreRejected(){
        for(key in listOf("expiresAtMs","brightness")){
            val json=snapshot();val i=json.getJSONObject("deviceModes").getJSONArray("instances").getJSONObject(0)
            i.put(key,if(key=="brightness")0 else now+99*MODE_DAY)
            assertThrows(IllegalArgumentException::class.java){RemoteSnapshotParser.parse(json,eligible,true)}
        }
    }
    @Test fun existingSchemaTwoMigratesWithoutModesOrRuleLoss(){
        val json=snapshot().put("schemaVersion",2);json.remove("deviceModes");val s=RemoteSnapshotParser.parse(json,eligible,true);assertTrue(s.deviceModes.instances.isEmpty());assertEquals(3L,s.revision)
    }
    @Test fun protectedAndConservativelyClassifiedAppsAreExcluded(){
        for((p,label,system)in listOf(Triple("com.mindful.android.remote.design","FocusRelay",false),Triple("com.android.settings","Settings",true),Triple("com.bank.app","Bank",false),Triple("com.example.authenticator","OTP",false),Triple("com.preinstalled.social","Social",true))){assertFalse(ModeSafety.eligibleName(p,label,system,setOf("com.mindful.android.remote.design")))}
        assertTrue(ModeSafety.eligibleName("com.example.video","Video",false,emptySet()))
    }
}
