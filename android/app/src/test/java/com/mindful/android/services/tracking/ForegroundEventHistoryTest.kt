// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
// SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.services.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundEventHistoryTest {
    private fun resume(at: Long, app: String) = ForegroundEventHistory.Event(at, app, "Main", ForegroundEventHistory.Action.RESUME)
    private fun pause(at: Long, app: String) = ForegroundEventHistory.Event(at, app, "Main", ForegroundEventHistory.Action.PAUSE)

    @Test fun lateResumeBeforeLastQueryBoundaryIsReplayed() {
        val history = ForegroundEventHistory()
        assertEquals("mindful", history.observe(listOf(resume(1000, "mindful")), 0))
        // Query through 2000 initially missed Reddit's 1800 event.
        assertEquals("mindful", history.observe(emptyList(), 0))
        assertEquals("reddit", history.observe(listOf(pause(1700, "mindful"), resume(1800, "reddit")), 0))
    }

    @Test fun repeatedOverlappingQueriesDoNotResurrectBackgroundApps() {
        val history = ForegroundEventHistory()
        val events = listOf(resume(1000,"reddit"), pause(1500,"reddit"), resume(1600,"mindful"))
        repeat(10) { assertEquals("mindful", history.observe(events, 0)) }
        assertEquals("mindful", history.observe(emptyList(), 6000))
        assertEquals("mindful", history.observe(emptyList(), 7000))
    }

    @Test fun latePauseIsAppliedInTimestampOrderRatherThanArrivalOrder() {
        val history = ForegroundEventHistory()
        history.observe(listOf(resume(1000,"coca"),resume(3000,"coca")),0)
        assertEquals("coca",history.observe(listOf(pause(2000,"coca")),0))
    }

    @Test fun currentAppPauseNeverFallsBackToOldRedditActivity() {
        val history = ForegroundEventHistory()
        history.observe(listOf(resume(1000,"reddit"),resume(2000,"mindful")),0)
        assertEquals("",history.observe(listOf(pause(3000,"mindful")),0))
    }

    @Test fun startupReplayAndPruningRetainCurrentAppAcrossQuietPolls() {
        val history = ForegroundEventHistory()
        assertEquals("coca",history.observe(listOf(resume(1,"old"),pause(2,"old"),resume(3,"coca")),10000))
        assertEquals("coca",history.observe(emptyList(),20000))
        assertEquals("",history.observe(listOf(pause(21000,"coca")),20000))
    }

    @Test fun resetAndUnlockCannotReplayThePreviouslyBlockedPackage() {
        val history = ForegroundEventHistory()
        history.observe(listOf(resume(1000,"reddit")),0)
        history.clear()
        assertEquals("mindful",history.observe(listOf(resume(2000,"mindful")),0))
        val reset = ForegroundEventHistory.Event(3000,"","",ForegroundEventHistory.Action.RESET)
        assertEquals("",history.observe(listOf(reset),0))
    }
}

