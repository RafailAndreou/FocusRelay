// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
// SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.localrules

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class LocalRuleEngineTest {
    private fun at(s: String) = Instant.parse(s).toEpochMilli()
    private val now = at("2026-10-22T08:18:00Z") // Thursday, 11:18 Nicosia
    private fun schedule(id: String = "study", end: String = "12:00", packages: Set<String> = setOf("reddit")) =
        LocalRule(id, id, "schedule", packages, days = setOf(4),
            times = listOf(RuleTime("09:00", end)), zone = "Asia/Nicosia")
    private fun budget(shared: Boolean = true) = LocalRule("budget", "Social", "usage", setOf("reddit", "instagram"),
        zone = "Asia/Nicosia", minutes = 30, shared = shared)
    @Test fun independentSchedulesCombine() {
        val e = LocalRuleEngine.evaluate(listOf(schedule(), schedule("video", packages = setOf("youtube"))), emptyMap(), now)
        assertEquals(setOf("reddit", "youtube"), e.blocked.keys)
    }
    @Test fun removingAnOverlapPreservesOtherEnd() {
        val a = schedule(end = "12:00"); val b = schedule("later", "13:00")
        assertEquals(at("2026-10-22T10:00:00Z"), LocalRuleEngine.evaluate(listOf(a,b), emptyMap(), now).blocked["reddit"])
        assertEquals(at("2026-10-22T09:00:00Z"), LocalRuleEngine.evaluate(listOf(a), emptyMap(), now).blocked["reddit"])
    }
    @Test fun pausedRuleDoesNotBlockOrEraseAnother() {
        val e = LocalRuleEngine.evaluate(listOf(schedule().copy(enabled = false), schedule("other")), emptyMap(), now)
        assertEquals("paused", e.states[0].phase)
        assertTrue(e.blocked.containsKey("reddit"))
    }
    @Test fun sharedAllowanceSumsApps() {
        val e = LocalRuleEngine.evaluate(listOf(budget()), mapOf("reddit" to 1000L, "instagram" to 800L), now)
        assertEquals(setOf("reddit", "instagram"), e.blocked.keys)
        assertEquals(1800L, e.states[0].usedSeconds)
    }
    @Test fun perAppAllowanceOnlyBlocksExhaustedApps() {
        val e = LocalRuleEngine.evaluate(listOf(budget(false)), mapOf("reddit" to 1800L, "instagram" to 600L), now)
        assertEquals(setOf("reddit"), e.blocked.keys)
    }
    @Test fun enabledBudgetIsNotBlockingUntilExhausted() {
        val e = LocalRuleEngine.evaluate(listOf(budget()), mapOf("reddit" to 600L), now)
        assertEquals("available", e.states[0].phase)
        assertTrue(e.blocked.isEmpty())
    }
    @Test fun pausingBudgetRetainsUsageButStopsItsEnforcement() {
        val e = LocalRuleEngine.evaluate(listOf(budget().copy(enabled = false)), mapOf("reddit" to 1800L), now)
        assertEquals("paused", e.states.single().phase)
        assertEquals(1800L, e.states.single().usedSeconds)
        assertTrue(e.blocked.isEmpty())
    }
    @Test fun quickExpiryDoesNotReleaseScheduledBlock() {
        val q = LocalRule("q", "Quick", "quick", setOf("reddit"), startAtMs = now-10000, endAtMs = now+1000)
        val e = LocalRuleEngine.evaluate(listOf(q, schedule()), emptyMap(), now+1000)
        assertEquals("expired", e.states[0].phase)
        assertTrue(e.blocked.containsKey("reddit"))
    }
    @Test fun overnightBelongsToStartingDay() {
        val r = schedule().copy(days = setOf(3), times = listOf(RuleTime("22:00", "07:00")))
        val e = LocalRuleEngine.evaluate(listOf(r), emptyMap(), at("2026-10-22T02:00:00Z"))
        assertEquals(at("2026-10-22T04:00:00Z"), e.blocked["reddit"])
    }
    @Test fun multipleWindowsHaveIndependentBoundaries() {
        val r = schedule().copy(times = listOf(RuleTime("09:00", "10:00"), RuleTime("12:00", "13:00")))
        val e = LocalRuleEngine.evaluate(listOf(r), emptyMap(), now)
        assertTrue(e.blocked.isEmpty())
        assertEquals("scheduled", e.states.single().phase)
        assertEquals(at("2026-10-22T09:00:00Z"), e.nextAtMs)
    }
    @Test fun endBoundaryIsExclusive() {
        assertFalse(LocalRuleEngine.evaluate(listOf(schedule()), emptyMap(), at("2026-10-22T09:00:00Z")).blocked.containsKey("reddit"))
    }
    @Test fun dailyResetRestoresAllowance() {
        val r = budget()
        val first = LocalRuleEngine.evaluate(listOf(r), mapOf("reddit" to 1800L), now)
        assertEquals(at("2026-10-22T21:00:00Z"), first.blocked["reddit"])
        assertTrue(LocalRuleEngine.evaluate(listOf(r), emptyMap(), at("2026-10-22T21:00:00Z")).blocked.isEmpty())
    }
    @Test fun dstOverlapUsesContinuousWindow() {
        val r = schedule().copy(days = setOf(7), times = listOf(RuleTime("03:00", "04:00")))
        val e = LocalRuleEngine.evaluate(listOf(r), emptyMap(), at("2026-10-25T01:30:00Z"))
        assertEquals("active", e.states.single().phase)
        assertEquals(at("2026-10-25T02:00:00Z"), e.blocked["reddit"])
    }
}

