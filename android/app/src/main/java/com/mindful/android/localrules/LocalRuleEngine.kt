// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
// SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.localrules

import com.mindful.android.remote.RemoteRuleEngine
import com.mindful.android.remote.RemoteTiming
import com.mindful.android.remote.RemoteWindow
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

data class RuleTime(val start: String, val end: String)
data class LocalRule(
    val id: String, val name: String, val kind: String, val packages: Set<String>,
    val enabled: Boolean = true, val days: Set<Int> = (1..7).toSet(),
    val times: List<RuleTime> = emptyList(), val zone: String = "UTC",
    val minutes: Int = 30, val shared: Boolean = true,
    val startAtMs: Long = 0, val endAtMs: Long = 0,
)
data class LocalRuleStatus(val rule: LocalRule, val phase: String, val usedSeconds: Long,
    val window: RemoteWindow?, val blocked: Map<String, Long>)
data class LocalEvaluation(val states: List<LocalRuleStatus>, val blocked: Map<String, Long>, val nextAtMs: Long?)

/** Independent restrictions combine by union: stopping one never releases another. */
object LocalRuleEngine {
    fun windows(rule: LocalRule, now: Long): List<RemoteWindow> = if (rule.kind == "quick") {
        RemoteRuleEngine.windows(RemoteTiming.Once(rule.startAtMs, rule.endAtMs), now)
    } else if (rule.kind == "schedule") rule.times.flatMap { t ->
        RemoteRuleEngine.windows(RemoteTiming.Weekly(rule.zone, rule.days,
            LocalTime.parse(t.start), LocalTime.parse(t.end)), now)
    }.sortedBy { it.startAtMs } else emptyList()
    fun evaluate(rules: List<LocalRule>, usage: Map<String, Long>, now: Long, usageZone: String? = null): LocalEvaluation {
        val all = mutableMapOf<String, Long>()
        val transitions = mutableListOf<Long>()
        val states = rules.map { rule ->
            val blocked = mutableMapOf<String, Long>()
            var window: RemoteWindow? = null
            val used = if (rule.kind != "usage") 0L else if (rule.shared)
                rule.packages.sumOf { usage[it] ?: 0L }
                else rule.packages.maxOfOrNull { usage[it] ?: 0L } ?: 0L
            val phase = if (!rule.enabled) "paused" else when (rule.kind) {
                "usage" -> {
                    val midnight = Instant.ofEpochMilli(now).atZone(ZoneId.of(usageZone ?: rule.zone)).toLocalDate()
                        .plusDays(1).atStartOfDay(ZoneId.of(usageZone ?: rule.zone)).toInstant().toEpochMilli()
                    transitions.add(midnight)
                    for (pkg in rule.packages) {
                        val consumed = if (rule.shared) used else usage[pkg] ?: 0L
                        if (consumed >= rule.minutes * 60L) blocked[pkg] = midnight
                    }
                    if (blocked.isEmpty()) "available" else "active"
                }
                else -> {
                    val windows = windows(rule, now)
                    val current = windows.filter { it.startAtMs <= now && it.endAtMs > now }
                    window = current.firstOrNull() ?: windows.firstOrNull()
                    for (w in windows) {
                        if (w.startAtMs > now) transitions.add(w.startAtMs)
                        if (w.endAtMs > now) transitions.add(w.endAtMs)
                    }
                    val end = current.maxOfOrNull { it.endAtMs }
                    if (end != null) for (pkg in rule.packages) blocked[pkg] = end
                    if (current.isNotEmpty()) "active" else if (windows.isEmpty()) "expired" else "scheduled"
                }
            }
            blocked.forEach { (pkg, end) -> all[pkg] = maxOf(all[pkg] ?: 0L, end) }
            LocalRuleStatus(rule, phase, used, window, blocked)
        }
        return LocalEvaluation(states, all, transitions.minOrNull())
    }
}

