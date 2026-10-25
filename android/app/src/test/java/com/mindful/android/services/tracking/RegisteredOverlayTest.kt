// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
// SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.services.tracking

import org.junit.Assert.*
import org.junit.Test

class RegisteredOverlayTest {
    private class FakeView(var attached: Boolean = false)
    private data class Target(val app: String, val remote: Boolean = true)
    private class Host {
        val registered = mutableSetOf<FakeView>()
        var failRemoval = false
        val overlay = RegisteredOverlay<FakeView, Target>(
            add = { check(registered.add(it)) },
            remove = {
                if (failRemoval) error("temporary window failure")
                check(registered.remove(it))
            },
        )
    }

    @Test fun unblockBeforeAttachmentUnregistersWindowInsteadOfLosingItsHandle() {
        val host = Host()
        val view = host.overlay.show(Target("coca")) { FakeView() }!!
        assertFalse(view.attached)
        host.overlay.clear()
        view.attached = true // A later traversal must not resurrect a registered window.
        assertTrue(host.registered.isEmpty())
        assertNull(host.overlay.view)
    }

    @Test fun revocationRemovesRemoteWindowWithoutAnotherForegroundEvent() {
        val host = Host()
        val active = mutableSetOf("coca")
        host.overlay.show(Target("coca")) { FakeView() }
        active.clear()
        host.overlay.clearIf { it.remote && it.app !in active }
        assertTrue(host.registered.isEmpty())
    }

    @Test fun revokingRemoteBlockDoesNotClearAnIndependentLocalOverlay() {
        val host = Host()
        host.overlay.show(Target("reddit", remote = false)) { FakeView() }
        host.overlay.clearIf { it.remote }
        assertEquals(1, host.registered.size)
    }

    @Test fun stoppedServiceRejectsLateDetectorAndReminderRequests() {
        val host = Host()
        host.overlay.show(Target("coca")) { FakeView() }
        host.overlay.close()
        assertNull(host.overlay.show(Target("coca")) { error("late inflate") })
        assertNull(host.overlay.show(Target("reddit")) { error("late reminder") })
        assertTrue(host.registered.isEmpty())
    }

    @Test fun failedRemovalKeepsHandleForLaterRetry() {
        val host = Host()
        host.overlay.show(Target("coca")) { FakeView() }
        host.failRemoval = true
        assertThrows(IllegalStateException::class.java) { host.overlay.clear() }
        assertNotNull(host.overlay.view)
        host.failRemoval = false
        host.overlay.clear()
        assertTrue(host.registered.isEmpty())
    }

    @Test fun closeCanRetryCleanupWithoutReopeningWindowCreation() {
        val host = Host()
        host.overlay.show(Target("coca")) { FakeView() }
        host.failRemoval = true
        assertThrows(IllegalStateException::class.java) { host.overlay.close() }
        assertTrue(host.overlay.closed)
        host.failRemoval = false
        host.overlay.close()
        host.overlay.close()
        assertTrue(host.registered.isEmpty())
    }

    @Test fun revokeDuringInflationCannotAddWindowAfterUnblock() {
        val host = Host()
        var active = true
        assertNull(host.overlay.show(Target("coca"), { active }) {
            active = false
            FakeView()
        })
        assertTrue(host.registered.isEmpty())
    }

    @Test fun repeatedRefreshBeforeAttachmentDoesNotCreateAnotherWindow() {
        val host = Host()
        val target = Target("coca")
        host.overlay.show(target) { FakeView() }
        repeat(10) { assertNull(host.overlay.show(target) { error("duplicate window") }) }
        assertEquals(1, host.registered.size)
        host.overlay.clear()
        assertTrue(host.registered.isEmpty())
    }

    @Test fun switchingAndRepeatedBlockingNeverLeavesMultipleWindows() {
        val host = Host()
        repeat(10) {
            host.overlay.show(Target("coca")) { FakeView() }
            host.overlay.show(Target("reddit")) { FakeView() }
            assertEquals(1, host.registered.size)
            host.overlay.clear()
            assertTrue(host.registered.isEmpty())
        }
    }
}

