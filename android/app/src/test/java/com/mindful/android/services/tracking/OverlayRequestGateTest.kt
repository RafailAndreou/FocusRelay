// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
// SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.services.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayRequestGateTest {
    @Test fun queuedRedditOverlayCannotAppearAfterSwitchingToMindful() {
        val gate = OverlayRequestGate()
        var overlay: String? = null
        val show = gate.next()
        val dismiss = gate.next()
        if (gate.isCurrent(show)) overlay = "reddit"
        if (gate.isCurrent(dismiss)) overlay = null
        assertEquals(null, overlay)
    }

    @Test fun olderDismissCannotRemoveNewCocaColaOverlay() {
        val gate = OverlayRequestGate()
        var overlay: String? = "reddit"
        val dismiss = gate.next()
        val show = gate.next()
        if (gate.isCurrent(dismiss)) overlay = null
        if (gate.isCurrent(show)) overlay = "coca"
        assertEquals("coca",overlay)
    }
    @Test fun disposalInvalidatesAnAlreadyQueuedShow() {
        val gate = OverlayRequestGate()
        val show = gate.next()
        gate.close()
        org.junit.Assert.assertFalse(gate.isCurrent(show))
    }

    @Test fun callbacksAfterDisposalCannotObtainAnAcceptedRequest() {
        val gate = OverlayRequestGate()
        gate.close()
        repeat(10) { org.junit.Assert.assertFalse(gate.isCurrent(gate.next())) }
    }
}

