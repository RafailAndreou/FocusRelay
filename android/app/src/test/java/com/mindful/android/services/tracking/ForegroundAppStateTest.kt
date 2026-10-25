// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
// SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.services.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundAppStateTest {
    @Test fun repeatedResumesDoNotLeaveAnAppActiveAfterPause() {
        val state = ForegroundAppState()
        repeat(10) { state.resume("old.app", "Main") }
        state.pause("old.app", "Main")
        state.resume("com.cocacola.app.cee", "Main")
        assertEquals("com.cocacola.app.cee", state.currentPackage)
        state.pause("com.cocacola.app.cee", "Main")
        assertEquals("", state.currentPackage)
    }

    @Test fun latestResumedAppWinsEvenWhenAnotherActivityRemainsResumed() {
        val state = ForegroundAppState()
        state.resume("old.app", "Main")
        state.resume("com.cocacola.app.cee", "Main")
        assertEquals("com.cocacola.app.cee", state.currentPackage)
    }

    @Test fun stoppingAnOlderActivityDoesNotRemoveTheAppsNewActivity() {
        val state = ForegroundAppState()
        state.resume("com.cocacola.app.cee", "Splash")
        state.resume("com.cocacola.app.cee", "Main")
        state.pause("com.cocacola.app.cee", "Splash")
        assertEquals("com.cocacola.app.cee", state.currentPackage)
    }

    @Test fun reconstructionReplacesEarlierSnapshot() {
        val state = ForegroundAppState()
        state.resume("old.app", "Main")
        state.clear()
        state.resume("com.cocacola.app.cee", "Main")
        state.pause("com.cocacola.app.cee", "Main")
        assertEquals("", state.currentPackage)
    }
}

