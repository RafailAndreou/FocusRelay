// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
// SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.remote

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class RemoteRuleEngineTest {
    private val app="com.instagram.android"
    private fun ms(s:String)=Instant.parse(s).toEpochMilli()
    private fun snapshot(vararg rules:RemoteRule, blocks:Map<String,Long> = emptyMap(), presets:List<RemotePreset> = emptyList())=RemoteSnapshot(7,blocks,rules.toList(),presets)
    private fun once(id:String="a",start:Long=1000,end:Long=3000)=RemoteRule(id,"Study",setOf(app),RemoteTiming.Once(start,end))
    private fun weekly(days:Set<Int> = setOf(1,2,3,4,5),start:String="20:00",end:String="22:00")=RemoteRule("a","Evening",setOf(app),RemoteTiming.Weekly("Asia/Nicosia",days,LocalTime.parse(start),LocalTime.parse(end)))
    private fun eval(s:RemoteSnapshot,time:Long,allowed:Set<String> = setOf(app))=RemoteRuleEngine.evaluate(s,allowed,time)
    @Test fun futureStartsAndEndsOfflineAtHalfOpenBoundaries() {
        val persisted=snapshot(once())
        assertTrue(eval(persisted,999).activeBlocks.isEmpty())
        assertEquals(1000L,eval(persisted,999).nextTransitionMs)
        assertEquals(3000L,eval(persisted,1000).activeBlocks[app])
        assertTrue(eval(persisted,3000).activeBlocks.isEmpty())
        assertEquals("expired",eval(persisted,3000).states.single().phase)
    }
    @Test fun overlapAndImmediateBlockUseUnionAndCannotShortenOneAnother() {
        val a=once(end=5000);val b=once("b",end=8000)
        assertEquals(9000L,eval(snapshot(a,b,blocks=mapOf(app to 9000L)),2000).activeBlocks[app])
        assertEquals(8000L,eval(snapshot(a.copy(deleted=true),b),6000).activeBlocks[app])
        assertTrue(eval(snapshot(a.copy(deleted=true),b.copy(deleted=true)),2000).activeBlocks.isEmpty())
    }
    @Test fun weeklyAndOvernightFollowStartDay() {
        val normal=snapshot(weekly())
        assertEquals("active",eval(normal,ms("2026-10-20T18:00:00Z")).states.single().phase)
        assertTrue(eval(normal,ms("2026-10-24T18:00:00Z")).activeBlocks.isEmpty())
        val night=snapshot(weekly(setOf(1),"23:30","07:30"))
        assertFalse(eval(night,ms("2026-10-20T03:00:00Z")).activeBlocks.isEmpty())
        assertTrue(eval(night,ms("2026-10-21T03:00:00Z")).activeBlocks.isEmpty())
    }
    @Test fun dstSpringGapAndAutumnOverlapMatchRelayPolicy() {
        val gap=weekly(setOf(7),"03:30","05:00")
        val gapWindow=RemoteRuleEngine.windows(gap.timing,ms("2026-03-28T00:00:00Z")).first()
        assertEquals(ms("2026-03-29T01:30:00Z"),gapWindow.startAtMs)
        val overlap=weekly(setOf(7),"03:15","03:45")
        val fall=RemoteRuleEngine.windows(overlap.timing,ms("2026-10-24T00:00:00Z")).first()
        assertEquals(ms("2026-10-25T00:15:00Z"),fall.startAtMs)
        assertEquals(ms("2026-10-25T01:45:00Z"),fall.endAtMs)
    }
    @Test fun overnightCrossingDstHasSevenOrNineRealHours() {
        val night=weekly(setOf(6),"23:30","07:30")
        val spring=RemoteRuleEngine.windows(night.timing,ms("2026-03-28T00:00:00Z")).first()
        val fall=RemoteRuleEngine.windows(night.timing,ms("2026-10-24T00:00:00Z")).first()
        assertEquals(7*3600000L,spring.endAtMs-spring.startAtMs)
        assertEquals(9*3600000L,fall.endAtMs-fall.startAtMs)
    }
    @Test fun pauseResumesLocallyWithoutExtendingOriginalExpiry() {
        val paused=once().copy(pausedUntilMs=2000)
        assertEquals("paused",eval(snapshot(paused),1500).states.single().phase)
        assertEquals(2000L,eval(snapshot(paused),1500).nextTransitionMs)
        assertEquals(3000L,eval(snapshot(paused),2000).activeBlocks[app])
        assertTrue(eval(snapshot(paused),3000).activeBlocks.isEmpty())
        assertNull(eval(snapshot(once().copy(pausedUntilMs=-1)),2000).nextTransitionMs)
    }
    @Test fun parentPresetPauseAndDeletionAffectOnlyLinkedInstances() {
        val linked=once().copy(presetId="study")
        val independent=once("other",end=6000)
        val parent=RemotePreset("study",pausedUntilMs=-1)
        val evaluated=eval(snapshot(linked,independent,presets=listOf(parent)),2000)
        assertEquals("paused",evaluated.states.first().phase)
        assertEquals(6000L,evaluated.activeBlocks[app])
        assertTrue(eval(snapshot(linked,presets=listOf(parent.copy(deleted=true))),2000).activeBlocks.isEmpty())
    }
    @Test fun restorationRecomputesCurrentPhaseFromSamePersistedSnapshot() {
        val persisted=snapshot(once())
        assertEquals(3000L,eval(persisted,2500).nextTransitionMs)
        assertTrue(eval(persisted,5000).activeBlocks.isEmpty())
        // Pure evaluation after reboot has no dependency on prior timer callbacks.
        assertEquals(eval(persisted,2500),eval(persisted.copy(),2500))
    }
    @Test fun revokedAllowlistImmediatelySuppressesRuleAndLegacyBlock() {
        assertTrue(eval(snapshot(once(),blocks=mapOf(app to 8000)),2000,emptySet()).activeBlocks.isEmpty())
    }
    @Test fun futureStartDateAndInclusiveEndDateKeepOvernightTail() {
        val weekly=RemoteTiming.Weekly("Asia/Nicosia",setOf(1),LocalTime.parse("23:30"),LocalTime.parse("07:30"),LocalDate.parse("2026-11-02"),LocalDate.parse("2026-11-02"))
        val r=RemoteRule("a","Dated",setOf(app),weekly)
        assertEquals(ms("2026-11-02T21:30:00Z"),eval(snapshot(r),ms("2026-10-20T00:00:00Z")).nextTransitionMs)
        assertFalse(eval(snapshot(r),ms("2026-11-03T03:00:00Z")).activeBlocks.isEmpty())
        assertEquals("expired",eval(snapshot(r),ms("2026-11-04T00:00:00Z")).states.single().phase)
    }
}

