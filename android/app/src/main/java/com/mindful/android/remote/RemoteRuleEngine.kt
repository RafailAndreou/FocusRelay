// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
// SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.remote

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

sealed class RemoteTiming {
    data class Once(val startAtMs: Long, val endAtMs: Long) : RemoteTiming()
    data class Weekly(val timeZone: String, val days: Set<Int>, val start: LocalTime, val end: LocalTime,
                      val startDate: LocalDate? = null, val endDate: LocalDate? = null) : RemoteTiming()
}
data class RemoteRule(val id: String, val name: String, val packages: Set<String>, val timing: RemoteTiming,
                      val presetId: String? = null, val pausedUntilMs: Long = 0, val deleted: Boolean = false, val disabled: Boolean = false)
data class RemotePreset(val id: String, val pausedUntilMs: Long = 0, val deleted: Boolean = false, val disabled: Boolean = false)
data class RemoteSnapshot(val revision: Long, val blocks: Map<String, Long>, val rules: List<RemoteRule>, val presets: List<RemotePreset>)
data class RemoteWindow(val startAtMs: Long, val endAtMs: Long)
data class RemoteRuleState(val id: String, val phase: String, val nextWindow: RemoteWindow?)
data class RemoteEvaluation(val activeBlocks: Map<String, Long>, val states: List<RemoteRuleState>, val nextTransitionMs: Long?)

/** Pure calendar/state engine. No Android, network, usage data, or upstream restrictions. */
object RemoteRuleEngine {
    private fun paused(until: Long, now: Long) = until == -1L || until > now
    fun windows(timing: RemoteTiming, now: Long): List<RemoteWindow> = when (timing) {
        is RemoteTiming.Once -> if (timing.endAtMs > now) listOf(RemoteWindow(timing.startAtMs, timing.endAtMs)) else emptyList()
        is RemoteTiming.Weekly -> {
            val zone = ZoneId.of(timing.timeZone)
            var date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().minusDays(1)
            if (timing.startDate != null && date < timing.startDate) date = timing.startDate
            val result = mutableListOf<RemoteWindow>()
            // Yesterday covers overnight windows; eight more days cover every ISO weekday.
            repeat(10) {
                if ((timing.startDate == null || date >= timing.startDate) &&
                    (timing.endDate == null || date <= timing.endDate) && date.dayOfWeek.value in timing.days) {
                    val endDate = if (timing.end < timing.start) date.plusDays(1) else date
                    // java.time shifts a gap forward by its length; choose a continuous overlap.
                    val start = date.atTime(timing.start).atZone(zone).withEarlierOffsetAtOverlap().toInstant().toEpochMilli()
                    val end = endDate.atTime(timing.end).atZone(zone).withLaterOffsetAtOverlap().toInstant().toEpochMilli()
                    if (end > start && end > now) result.add(RemoteWindow(start, end))
                }
                date = date.plusDays(1)
            }
            result
        }
    }
    fun evaluate(snapshot: RemoteSnapshot, allowed: Set<String>, now: Long): RemoteEvaluation {
        val active = mutableMapOf<String, Long>()
        val transitions = mutableListOf<Long>()
        val states = mutableListOf<RemoteRuleState>()
        for ((pkg, end) in snapshot.blocks) if (pkg in allowed && end > now) {active[pkg] = end; transitions.add(end)}
        val parents = snapshot.presets.associateBy { it.id }
        for (rule in snapshot.rules) {
            val parent = rule.presetId?.let { parents[it] }
            val phase: String
            var nextWindow: RemoteWindow? = null
            when {
                rule.deleted || parent?.deleted == true -> phase = "deleted"
                rule.disabled || parent?.disabled == true || rule.packages.none { it in allowed } -> phase = "unavailable"
                else -> {
                    val upcoming = windows(rule.timing, now)
                    nextWindow = upcoming.firstOrNull()
                    if (upcoming.isEmpty()) {
                        phase = "expired"
                    } else if (paused(rule.pausedUntilMs, now) || paused(parent?.pausedUntilMs ?: 0, now)) {
                        phase = "paused"
                        listOf(rule.pausedUntilMs, parent?.pausedUntilMs ?: 0).filter { it > now }.forEach { transitions.add(it) }
                    } else {
                        val current = upcoming.firstOrNull { it.startAtMs <= now && it.endAtMs > now }
                        phase = if (current != null) "active" else if (upcoming.isEmpty()) "expired" else "scheduled"
                        if (current != null) for (pkg in rule.packages.filter { it in allowed }) active[pkg] = maxOf(active[pkg] ?: 0, current.endAtMs)
                        for (window in upcoming) {
                            if (window.startAtMs > now) transitions.add(window.startAtMs)
                            if (window.endAtMs > now) transitions.add(window.endAtMs)
                        }
                    }
                }
            }
            states.add(RemoteRuleState(rule.id, phase, nextWindow))
        }
        return RemoteEvaluation(active, states, transitions.minOrNull())
    }
}

