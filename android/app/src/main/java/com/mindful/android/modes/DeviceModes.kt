// Copyright 2026 Rafail Andreou. SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.modes

import com.mindful.android.remote.RemoteTiming
import com.mindful.android.remote.RemoteRuleEngine
import com.mindful.android.remote.RemoteSnapshotParser
import com.mindful.android.remote.RemoteWindow
import org.json.JSONArray
import org.json.JSONObject

const val MODE_DAY = 86400000L

data class DeviceModeInstance(val id: String, val templateId: String, val name: String, val mode: String,
    val allowedPackages: Set<String>, val blockedPackages: Set<String>, val excludedPackages: Set<String>,
    val timing: RemoteTiming, val expiresAtMs: Long, val createdAtMs: Long, val actions: Map<String, Int> = emptyMap(),
    val alarmAtMs: Long? = null, val pausedUntilMs: Long = 0, val stoppedAtMs: Long? = null)
data class TemporaryException(val id: String, val instanceId: String, val packageName: String,
    val createdAtMs: Long, val untilMs: Long, val revokedAtMs: Long? = null)
data class DeviceModeState(val instances: List<DeviceModeInstance> = emptyList(), val exceptions: List<TemporaryException> = emptyList())
data class DeviceModeStatus(val instance: DeviceModeInstance, val phase: String, val window: RemoteWindow?, val blocked: Set<String>)
data class DeviceModeEvaluation(val blocks: Map<String, Long>, val states: List<DeviceModeStatus>,
    val exceptions: List<TemporaryException>, val nextAtMs: Long?)

/** Pure finite-state evaluator: no installed-app snapshot or expanded allow-list complement is stored. */
object DeviceModeEngine {
    fun settingsRequests(e: DeviceModeEvaluation?, consent: Boolean): Map<String,Int> {
        val result=mutableMapOf<String,Int>();if(!consent)return result
        for(s in e?.states?:emptyList())if(s.phase=="active")for((k,v)in s.instance.actions)result[k]=minOf(result[k]?:Int.MAX_VALUE,v)
        return result
    }
    fun evaluate(state: DeviceModeState, eligible: Set<String>, now: Long, enabled: Boolean = true,
                 stopped: Set<String> = emptySet()): DeviceModeEvaluation {
        val denied = mutableMapOf<String, Long>(); val times = mutableListOf<Long>(); val exceptions = mutableListOf<TemporaryException>()
        val states = state.instances.map { i ->
            val windows = RemoteRuleEngine.windows(i.timing, now).filter { it.startAtMs < i.expiresAtMs }
            val current = windows.firstOrNull { it.startAtMs <= now && it.endAtMs > now && i.expiresAtMs > now }
            val phase = when {
                i.id in stopped || i.stoppedAtMs != null -> "stopped"
                !enabled -> "disabled"
                i.expiresAtMs <= now || windows.isEmpty() -> "expired"
                i.pausedUntilMs == -1L || i.pausedUntilMs > now -> "paused"
                current != null -> "active"
                else -> "scheduled"
            }
            if (enabled && phase !in setOf("stopped", "expired")) {
                times.add(i.expiresAtMs)
                if (i.pausedUntilMs > now) times.add(i.pausedUntilMs)
                for (w in windows) { if (w.startAtMs > now) times.add(w.startAtMs); if (w.endAtMs > now) times.add(minOf(w.endAtMs, i.expiresAtMs)) }
            }
            val blocked = mutableSetOf<String>()
            if (phase == "active" && current != null) {
                val granted = state.exceptions.filter { it.instanceId == i.id && it.revokedAtMs == null && it.createdAtMs <= now && it.untilMs > now }
                exceptions.addAll(granted);times.addAll(granted.map { it.untilMs })
                val permitted = granted.map { it.packageName }.toSet() + i.excludedPackages
                val candidates = if (i.mode == "allow_list") eligible - i.allowedPackages else eligible.intersect(i.blockedPackages)
                blocked.addAll(candidates - permitted)
                for (pkg in blocked) denied[pkg] = maxOf(denied[pkg] ?: 0, minOf(current.endAtMs, i.expiresAtMs))
            }
            DeviceModeStatus(i, phase, current, blocked)
        }
        return DeviceModeEvaluation(denied, states, exceptions, times.filter { it > now }.minOrNull())
    }
}

object DeviceModeParser {
    private val pkg = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")
    private fun number(o: JSONObject, key: String): Long {
        val n = o.get(key); require(n is Number && n.toDouble().isFinite() && n.toDouble() == n.toLong().toDouble() && kotlin.math.abs(n.toDouble()) <= 9007199254740991.0) { "Invalid mode number: $key" };return n.toLong()
    }
    private fun text(o: JSONObject, key: String, max: Int = 80): String { val s = o.get(key);require(s is String && s.isNotBlank() && s.length <= max && s.none { it < ' ' });return s }
    private fun list(a: JSONArray, allowed: Set<String>? = null): Set<String> {
        require(a.length() <= 500);val values = (0 until a.length()).map { a.get(it) }
        require(values.all { it is String && it.length <= 255 && pkg.matches(it) && (allowed == null || it in allowed) }) { "Invalid mode package" }
        val result = values.map { it as String }.toSet();require(result.size == a.length());return result
    }
    private fun definition(o: JSONObject, allowed: Set<String>) {
        text(o,"id");text(o,"name");if(o.has("description")) { val v=o.get("description");require(v is String && v.length<=500 && v.none { it<' ' }) }
        if(o.has("icon"))text(o,"icon",24)
        val mode=text(o,"mode");require(mode in setOf("block_list","allow_list"))
        val allow=list(o.getJSONArray("allowedPackages"));val block=list(o.getJSONArray("blockedPackages"),allowed)
        list(o.getJSONArray("excludedPackages"))
        require(mode!="allow_list" || allow.isNotEmpty() && block.isEmpty()) { "Empty or mixed allow-list" }
        require(mode!="block_list" || allow.isEmpty())
        if(!o.isNull("durationMinutes"))require(number(o,"durationMinutes") in 1..10080)
        if(!o.isNull("timing"))RemoteSnapshotParser.timing(o.getJSONObject("timing"))
        for((k,min) in listOf("brightness" to 5,"mediaVolume" to 0,"ringtoneVolume" to 1))if(o.has(k))require(number(o,k) in min.toLong()..100L)
        if(o.has("dnd"))require(o.get("dnd") is Boolean)
        if(o.has("notificationPolicy"))require(o.getString("notificationPolicy") in setOf("unchanged","alarms_only"))
    }
    fun parse(o: JSONObject, allowed: Set<String>): DeviceModeState {
        require(number(o,"schemaVersion")==1L)
        com.mindful.android.alarms.AlarmPolicy.validateAll(o.optJSONArray("alarms")?:JSONArray())
        val templates=o.getJSONArray("templates");require(templates.length()<=32)
        val templateIds=mutableSetOf<String>()
        for(n in 0 until templates.length()){val t=templates.getJSONObject(n);definition(t,allowed);require(templateIds.add(t.getString("id")))}
        val a=o.getJSONArray("instances");require(a.length()<=64)
        val instances=(0 until a.length()).map { n ->
            val i=a.getJSONObject(n);definition(i,allowed)
            val timing=RemoteSnapshotParser.timing(i.getJSONObject("timing"));val created=number(i,"createdAtMs");val expiry=number(i,"expiresAtMs")
            require(created>=0 && expiry>created)
            when(timing){is RemoteTiming.Once -> require(expiry==timing.endAtMs && timing.startAtMs>=created-300000 && timing.startAtMs<=created+366*MODE_DAY)
                is RemoteTiming.Weekly -> require(expiry<=created+30*MODE_DAY)}
            val actions=mutableMapOf<String,Int>()
            for(k in listOf("brightness","mediaVolume","ringtoneVolume"))if(i.has(k))actions[k]=number(i,k).toInt()
            if(i.optBoolean("dnd",false)||i.optString("notificationPolicy")=="alarms_only")actions["dnd"]=1
            val alarm=if(i.has("alarmAtMs"))number(i,"alarmAtMs")else null
            require(alarm==null || alarm in (created+1)..(created+MODE_DAY))
            val pause=number(i,"pausedUntilMs");require(pause>=-1)
            val stopped=if(i.has("stoppedAtMs"))number(i,"stoppedAtMs")else null
            require(stopped==null||stopped>=created)
            DeviceModeInstance(text(i,"id"),text(i,"templateId"),text(i,"name"),i.getString("mode"),list(i.getJSONArray("allowedPackages")),list(i.getJSONArray("blockedPackages"),allowed),list(i.getJSONArray("excludedPackages")),timing,expiry,created,actions,alarm,pause,stopped)
        }
        require(instances.map{it.id}.toSet().size==instances.size)
        val e=o.getJSONArray("exceptions");require(e.length()<=64)
        val exceptions=(0 until e.length()).map { n ->
            val x=e.getJSONObject(n);val p=text(x,"packageName",255);require(pkg.matches(p)&&p in allowed)
            val created=number(x,"createdAtMs");val end=number(x,"untilMs");require(created>=0&&end>created&&end-created<=3600000)
            val parent=text(x,"instanceId");require(instances.any{it.id==parent&&end<=it.expiresAtMs})
            TemporaryException(text(x,"id"),parent,p,created,end,if(x.has("revokedAtMs"))number(x,"revokedAtMs")else null)
        }
        require(exceptions.map{it.id}.toSet().size==exceptions.size)
        return DeviceModeState(instances,exceptions)
    }
}
