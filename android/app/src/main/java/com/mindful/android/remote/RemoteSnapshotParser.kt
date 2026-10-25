// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
// SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.remote

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Validate the entire envelope before any preference or acknowledgement is changed. */
object RemoteSnapshotParser {
    private val packagePattern = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")
    private fun integer(o: JSONObject, key: String): Long {
        val v = o.get(key)
        require(v is Number && v.toDouble().isFinite() && v.toDouble() == v.toLong().toDouble() && kotlin.math.abs(v.toDouble()) <= 9007199254740991.0) { "Invalid integer: $key" }
        return v.toLong()
    }
    private fun text(o: JSONObject, key: String, max: Int = 80): String {
        val v = o.get(key);require(v is String && v.isNotBlank() && v.length <= max) { "Invalid text: $key" };return v
    }
    private fun flag(o: JSONObject, key: String): Boolean {if (!o.has(key)) return false;val v=o.get(key);require(v is Boolean);return v}
    private fun items(a: JSONArray, max: Int): List<JSONObject> {require(a.length() <= max) { "Collection too large" };return (0 until a.length()).map { a.getJSONObject(it) }}
    private fun packages(a: JSONArray, allowed: Set<String>, empty: Boolean): Set<String> {
        require(a.length() <= 50 && (a.length() > 0 || empty)) { "Invalid package count" }
        val values=(0 until a.length()).map { a.get(it) }
        require(values.all { it is String && it.length <= 255 && packagePattern.matches(it) && it in allowed }) { "Package is not locally allowed" }
        val result=values.map { it as String }.toSet();require(result.size==a.length()) { "Duplicate package" };return result
    }
    private fun pause(o: JSONObject): Long {val v=integer(o,"pausedUntilMs");require(v>=-1);return v}
    private fun timing(o: JSONObject): RemoteTiming = when (o.getString("type")) {
        "once" -> {
            val start=integer(o,"startAtMs");val end=integer(o,"endAtMs")
            require(start>=0 && end>start && end-start<=7L*86400000) { "Invalid one-time window" }
            RemoteTiming.Once(start,end)
        }
        "weekly" -> {
            val zone=text(o,"timeZone");ZoneId.of(zone)
            val a=o.getJSONArray("daysOfWeek");require(a.length() in 1..7)
            val days=(0 until a.length()).map { val v=a.get(it);require(v is Number && v.toInt().toDouble()==v.toDouble() && v.toInt() in 1..7);v.toInt() }.toSet()
            require(days.size==a.length())
            val startText=text(o,"startTime");val endText=text(o,"endTime")
            require(Regex("(?:[01]\\d|2[0-3]):[0-5]\\d").matches(startText)&&Regex("(?:[01]\\d|2[0-3]):[0-5]\\d").matches(endText))
            val start=LocalTime.parse(startText);val end=LocalTime.parse(endText);require(start!=end)
            val startDate=if(o.has("startDate"))LocalDate.parse(text(o,"startDate"))else null
            val endDate=if(o.has("endDate"))LocalDate.parse(text(o,"endDate"))else null
            require(startDate==null || endDate==null || endDate>=startDate)
            RemoteTiming.Weekly(zone,days,start,end,startDate,endDate)
        }
        else -> throw IllegalArgumentException("Unknown timing type")
    }
    fun validateAndPersist(json: JSONObject, allowed: Set<String>, previousRevision: Long,
                           requireV2: Boolean, persist: (RemoteSnapshot) -> Boolean): RemoteSnapshot {
        val parsed = parse(json, allowed, requireV2)
        require(parsed.revision >= previousRevision) { "Stale desired revision" }
        check(persist(parsed)) { "Cannot persist full desired snapshot" }
        return parsed
    }
    fun parse(json: JSONObject, allowed: Set<String>, requireV2: Boolean = false): RemoteSnapshot {
        val revision=integer(json,"revision");require(revision>=0)
        require(integer(json,"serverTimeMs")>=0)
        val blocks=items(json.getJSONArray("blocks"),500).associate { b ->
            val pkg=text(b,"packageName",255);require(packagePattern.matches(pkg)&&pkg in allowed) { "Block is not locally allowed" }
            val end=integer(b,"untilMs");require(end>=0);pkg to end
        }
        require(blocks.size==json.getJSONArray("blocks").length()) { "Duplicate immediate block" }
        if(!json.has("schemaVersion")){require(!requireV2) { "Refusing legacy snapshot after receiving schedules" };return RemoteSnapshot(revision,blocks,emptyList(),emptyList())}
        require(integer(json,"schemaVersion")==2L) { "Unsupported desired schema" }
        val presetObjects=items(json.getJSONArray("presets"),80)
        val presets=presetObjects.map { p ->
            val deleted=p.has("deletedAtMs")&&integer(p,"deletedAtMs")>0
            val disabled=flag(p,"disabled");text(p,"name");packages(p.getJSONArray("packages"),allowed,deleted||disabled)
            if(!p.isNull("durationMinutes")){require(integer(p,"durationMinutes") in 1L..10080L)}
            if(!p.isNull("timing"))timing(p.getJSONObject("timing"))
            RemotePreset(text(p,"id"),pause(p),deleted,disabled)
        }
        require(presets.map{it.id}.toSet().size==presets.size && presets.count{!it.deleted}<=16)
        val rules=items(json.getJSONArray("schedules"),128).map { r ->
            val deleted=r.has("deletedAtMs")&&integer(r,"deletedAtMs")>0
            val disabled=flag(r,"disabled")
            val parent=if(r.has("presetId"))text(r,"presetId")else null
            require(parent==null||presets.any{it.id==parent}) { "Unknown preset reference" }
            RemoteRule(text(r,"id"),text(r,"name"),packages(r.getJSONArray("packages"),allowed,deleted||disabled),timing(r.getJSONObject("timing")),parent,pause(r),deleted,disabled)
        }
        require(rules.map{it.id}.toSet().size==rules.size && rules.count{!it.deleted}<=64)
        return RemoteSnapshot(revision,blocks,rules,presets)
    }
}

