// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
// SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.services.tracking

/** Invalidates UI work queued for an app that is no longer the blocking target. */
internal class OverlayRequestGate {
    private var revision = 0L
    private var closed = false
    @Synchronized fun next(): Long = ++revision
    @Synchronized fun isCurrent(request: Long): Boolean = !closed && request == revision
    @Synchronized fun close() { closed = true; ++revision }
}

