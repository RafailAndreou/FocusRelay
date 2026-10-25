// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
// SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.localrules

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.SystemClock
import com.mindful.android.helpers.usages.ScreenUsageHelper
import com.mindful.android.remote.RemoteStore
import com.mindful.android.models.RestrictionState
import com.mindful.android.enums.RestrictionType
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId

/** Device-only rules. Never written into the relay's desired-state snapshot. */
object LocalRules {
    private fun prefs(c: Context) = c.getSharedPreferences("mindful_local_rules_v1", Context.MODE_PRIVATE)
    @Volatile private var usageAt = -10000L
    @Volatile private var usageDay = ""
    @Volatile private var usageCache = emptyMap<String, Long>()
    private var rulesCache: List<LocalRule>? = null
    private fun decode(o: JSONObject): LocalRule {
        fun strings(a: JSONArray) = (0 until a.length()).map { a.getString(it) }.toSet()
        val times = o.optJSONArray("times") ?: JSONArray()
        val days = o.optJSONArray("days") ?: JSONArray((1..7).toList())
        return LocalRule(o.getString("id"), o.getString("name"), o.getString("kind"),
            strings(o.getJSONArray("packages")), o.optBoolean("enabled", true),
            (0 until days.length()).map { days.getInt(it) }.toSet(),
            (0 until times.length()).map { RuleTime(times.getJSONObject(it).getString("start"), times.getJSONObject(it).getString("end")) },
            o.optString("zone", ZoneId.systemDefault().id), o.optInt("minutes", 30),
            o.optBoolean("shared", true), o.optLong("startAtMs"), o.optLong("endAtMs"))
    }
    fun json(r: LocalRule) = JSONObject().put("id", r.id).put("name", r.name).put("kind", r.kind)
        .put("packages", JSONArray(r.packages.toList())).put("enabled", r.enabled)
        .put("days", JSONArray(r.days.toList())).put("zone", r.zone).put("minutes", r.minutes)
        .put("shared", r.shared).put("startAtMs", r.startAtMs).put("endAtMs", r.endAtMs)
        .put("times", JSONArray(r.times.map { JSONObject().put("start", it.start).put("end", it.end) }))
    @Synchronized fun rules(c: Context): List<LocalRule> {
        rulesCache?.let { return it }
        val a = JSONArray(prefs(c).getString("rules", "[]"))
        return (0 until a.length()).map { decode(a.getJSONObject(it)) }.also { rulesCache = it }
    }
    @Synchronized private fun persist(c: Context, rules: List<LocalRule>) {
        require(prefs(c).edit().putString("rules", JSONArray(rules.map(::json)).toString()).commit()) { "Could not save rules" }
        rulesCache = rules
    }
    @Synchronized fun save(c: Context, input: Map<*, *>) {
        val r = decode(JSONObject(input))
        require(r.id.isNotBlank() && r.id.length <= 100 && r.name.isNotBlank() && r.name.length <= 80)
        require(r.kind in setOf("quick", "schedule", "usage"))
        require(r.packages.isNotEmpty() && r.packages.size <= 500)
        val eligible = RemoteStore.availableApps(c).map { it["packageName"] }.toSet()
        require(r.packages.all { it in eligible }) { "Select installed apps; essential controls cannot be blocked" }
        ZoneId.of(r.zone)
        require(r.minutes in 1..1440)
        if (r.kind == "schedule") {
            require(r.days.isNotEmpty() && r.days.all { it in 1..7 })
            require(r.times.isNotEmpty() && r.times.size <= 12)
            r.times.forEach { require(java.time.LocalTime.parse(it.start) != java.time.LocalTime.parse(it.end)) { "Start and end must differ" } }
        }
        if (r.kind == "quick") require(r.endAtMs > r.startAtMs && r.endAtMs - r.startAtMs <= 86400000L)
        val others = rules(c).filter { it.id != r.id && (it.kind != "quick" || it.endAtMs > System.currentTimeMillis()) }
        require(others.size < 100) { "Up to 100 rules are supported" }
        persist(c, others + r)
    }
    @Synchronized fun delete(c: Context, id: String) = persist(c, rules(c).filter { it.id != id })
    @Synchronized fun toggle(c: Context, id: String, enabled: Boolean) = persist(c,
        rules(c).map { if (it.id == id) it.copy(enabled = enabled) else it })
    fun hasEnabled(c: Context): Boolean = rules(c).any { it.enabled && (it.kind != "quick" || it.endAtMs > System.currentTimeMillis()) }
    fun usage(c: Context, fresh: Boolean = false): Map<String, Long> {
        val day = java.time.LocalDate.now().toString()
        // WindowManager callbacks must not scan a day's usage events on the UI thread.
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) return if (day == usageDay) usageCache else emptyMap()
        if (fresh || day != usageDay || SystemClock.elapsedRealtime() - usageAt > 1500) {
            usageCache = ScreenUsageHelper.fetchAppUsageTodayTillNow(c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager)
            usageDay = day; usageAt = SystemClock.elapsedRealtime()
        }
        return usageCache
    }
    fun evaluation(c: Context, fresh: Boolean = false): LocalEvaluation {
        val rules = rules(c)
        return LocalRuleEngine.evaluate(rules, if (rules.any { it.enabled && it.kind == "usage" }) usage(c, fresh) else emptyMap(), System.currentTimeMillis(), ZoneId.systemDefault().id)
    }
    fun restriction(c: Context, pkg: String): RestrictionState? {
        val e = evaluation(c)
        val active = e.states.firstOrNull { pkg in it.blocked }
        if (active != null) return RestrictionState(RestrictionType.LOCAL_RULE, groupName = active.rule.name)
        return e.states.filter { it.rule.enabled && it.rule.kind == "usage" && pkg in it.rule.packages }.map { state ->
            val used = if (state.rule.shared) state.usedSeconds else usage(c)[pkg] ?: 0L
            RestrictionState(RestrictionType.LOCAL_RULE, groupName = state.rule.name,
                timeLeftMillis = maxOf(1L, (state.rule.minutes * 60L - used) * 1000L))
        }.minByOrNull { it.timeLeftMillis }
    }
    // Only usage windows which were actually added by WindowManager are recorded.
    @Synchronized fun recordBlock(c: Context, pkg: String, name: String) {
        val a = JSONArray(prefs(c).getString("activity", "[]"))
        val now = System.currentTimeMillis()
        if (a.length() > 0 && a.getJSONObject(0).optString("package") == pkg && now - a.getJSONObject(0).optLong("at") < 5000) return
        val next = JSONArray().put(JSONObject().put("package", pkg).put("name", name).put("at", now))
        for (i in 0 until minOf(a.length(), 199)) next.put(a.getJSONObject(i))
        prefs(c).edit().putString("activity", next.toString()).apply()
    }
    fun state(c: Context): Map<String, Any?> {
        val now = System.currentTimeMillis()
        val usage = usage(c, true)
        val e = LocalRuleEngine.evaluate(rules(c), usage, now, ZoneId.systemDefault().id)
        val activity = JSONArray(prefs(c).getString("activity", "[]"))
        val start = java.time.LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return mapOf("nowMs" to now, "zone" to ZoneId.systemDefault().id,
            "screenSeconds" to usage.values.sum(), "usage" to usage,
            "quickPackages" to (prefs(c).getStringSet("quickPackages", emptySet())?.toList() ?: emptyList<String>()),
            "quickMinutes" to prefs(c).getInt("quickMinutes", 30),
            "rules" to e.states.map { s ->
                mapOf("rule" to json(s.rule).toString(), "phase" to s.phase, "usedSeconds" to s.usedSeconds,
                    "blockedPackages" to s.blocked.keys.toList(), "startMs" to s.window?.startAtMs, "endMs" to s.window?.endAtMs,
                    "windows" to LocalRuleEngine.windows(s.rule, start).filter { it.startAtMs < java.time.LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
                        .map { mapOf("start" to it.startAtMs, "end" to it.endAtMs) })
            },
            "activity" to (0 until activity.length()).map { activity.getJSONObject(it) }.filter { it.optLong("at") >= start }.map {
                mapOf("package" to it.optString("package"), "name" to it.optString("name"), "at" to it.optLong("at"))
            })
    }
    fun configureQuick(c: Context, packages: Set<String>, minutes: Int) {
        require(minutes in 1..1440)
        val eligible = RemoteStore.availableApps(c).map { it["packageName"] }.toSet()
        require(packages.isNotEmpty() && packages.all { it in eligible })
        require(prefs(c).edit().putStringSet("quickPackages", packages).putInt("quickMinutes", minutes).commit())
    }
}

