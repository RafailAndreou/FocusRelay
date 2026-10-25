// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
// SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.services.tracking

/** Replays the overlapping lifecycle tail so late events and duplicate reads are safe. */
internal class ForegroundEventHistory {
    companion object { const val LOOKBACK_MS = 5000L }
    enum class Action { RESUME, PAUSE, RESET }
    data class Event(val at: Long, val packageName: String, val className: String, val action: Action)
    private var baseline = ForegroundAppState()
    private val pending = linkedSetOf<Event>()

    fun clear() { baseline.clear(); pending.clear() }

    fun observe(events: List<Event>, retainFrom: Long): String {
        pending.addAll(events)
        val ordered = pending.sortedBy { it.at }
        for (event in ordered.filter { it.at < retainFrom }) apply(baseline, event)
        pending.removeAll { it.at < retainFrom }
        val current = baseline.copy()
        for (event in ordered.filter { it.at >= retainFrom }) apply(current, event)
        return current.currentPackage
    }

    private fun apply(state: ForegroundAppState, event: Event) {
        when (event.action) {
            Action.RESET -> state.clear()
            Action.RESUME -> state.resume(event.packageName, event.className)
            Action.PAUSE -> state.pause(event.packageName, event.className)
        }
    }
}

