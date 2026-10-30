// Copyright 2026 Rafail Andreou. SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.modes

import android.content.Context
import com.mindful.android.remote.RemoteStore
import com.mindful.android.remote.RemoteEvaluation
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID

object DeviceModeStore {
    fun prefs(c: Context)=c.getSharedPreferences("focusrelay_device_modes_v1",Context.MODE_PRIVATE)
    fun enabled(c: Context)=prefs(c).getBoolean("enabled",false)
    fun actionsConsent(c: Context)=prefs(c).getBoolean("actions",false)
    fun usageSharing(c: Context)=prefs(c).getBoolean("usage",false)
    fun excluded(c: Context)=prefs(c).getStringSet("excluded",emptySet())?.toSet()?:emptySet()
    @Volatile private var eligibleCache: Triple<Long,Set<String>,Set<String>>?=null
    fun eligible(c: Context): Set<String> {
        val input=RemoteStore.allowedPackages(c);val exclude=excluded(c);val key=input+exclude.map{"exclude:$it"}
        val now=android.os.SystemClock.elapsedRealtime();val cache=eligibleCache
        if(cache!=null&&cache.second==key&&now>=cache.first&&now-cache.first<30000)return cache.third
        return ModeSafety.eligible(c,input,exclude).also{eligibleCache=Triple(now,key,it)}
    }
    fun setup(c: Context, enabled: Boolean, actions: Boolean, usage: Boolean, exclusions: Set<String>) {
        require(RemoteStore.enabled(c)) { "Connect remote control first" }
        require(exclusions.all{it in RemoteStore.allowedPackages(c)}) { "Unknown exclusion" }
        check(prefs(c).edit().putBoolean("enabled",enabled).putBoolean("actions",actions).putBoolean("usage",usage).putStringSet("excluded",exclusions).commit())
        eligibleCache=null;RemoteStore.invalidateEvaluation()
    }
    fun stopped(c: Context): Set<String> {
        val o=JSONObject(prefs(c).getString("stopped","{}")!!);val now=System.currentTimeMillis()
        return o.keys().asSequence().filter{o.optLong(it)>now}.toSet()
    }
    fun stop(c: Context, id: String) {
        val a=RemoteStore.modeJson(c).optJSONArray("instances")?:JSONArray()
        val instance=(0 until a.length()).map{a.getJSONObject(it)}.firstOrNull{it.getString("id")==id}?:throw IllegalArgumentException("Unknown mode instance")
        val o=JSONObject(prefs(c).getString("stopped","{}")!!);val now=System.currentTimeMillis()
        o.keys().asSequence().toList().filter{o.optLong(it)<=now}.forEach{o.remove(it)}
        require(o.length()<128||o.has(id)) { "Local recovery limit reached; use emergency stop" }
        o.put(id,instance.getLong("expiresAtMs"))
        check(prefs(c).edit().putString("stopped",o.toString()).commit())
        RemoteStore.invalidateEvaluation()
    }
    fun emergencyStop(c: Context) {
        check(prefs(c).edit().putBoolean("enabled",false).commit())
        val a=RemoteStore.modeJson(c).optJSONArray("instances")?:JSONArray()
        for(n in 0 until a.length())runCatching{stop(c,a.getJSONObject(n).getString("id"))}
        event(c,"emergency_stop",mapOf("reason" to "local_user"));RemoteStore.invalidateEvaluation()
    }
    private val eventLock=Any()
    fun event(c: Context, kind: String, details: Map<String,Any>, eventId: String=UUID.randomUUID().toString())=synchronized(eventLock){
        val p=prefs(c);val old=JSONArray(p.getString("events","[]")!!);val now=System.currentTimeMillis()
        val values=(0 until old.length()).map{old.getJSONObject(it)}.filter{it.optLong("atMs")>now-30*MODE_DAY}.toMutableList()
        if(values.any{it.optString("id")==eventId})return@synchronized
        if(kind=="blocked_app_opened"&&values.any{it.getString("kind")==kind&&it.getJSONObject("details").optString("packageName")==details["packageName"]&&it.getLong("atMs")>now-5000})return@synchronized
        values.add(JSONObject().put("id",eventId).put("atMs",now).put("kind",kind).put("details",JSONObject(details)).put("uploaded",false))
        check(p.edit().putString("events",JSONArray(values.takeLast(500)).toString()).commit())
    }
    fun pendingEvents(c: Context): JSONArray {
        val a=JSONArray(prefs(c).getString("events","[]")!!);val result=JSONArray();val now=System.currentTimeMillis()
        for(i in 0 until a.length()) {val e=a.getJSONObject(i);if(!e.optBoolean("uploaded")&&e.getLong("atMs")>now-30*MODE_DAY&&result.length()<32){val copy=JSONObject(e.toString());copy.remove("uploaded");result.put(copy)}}
        return result
    }
    fun acknowledgeEvents(c: Context, ids: JSONArray?)=synchronized(eventLock){
        if(ids==null)return@synchronized
        val set=(0 until ids.length()).map{ids.getString(it)}.toSet();val p=prefs(c);val a=JSONArray(p.getString("events","[]")!!)
        for(i in 0 until a.length())a.getJSONObject(i).let{if(it.getString("id") in set)it.put("uploaded",true)}
        check(p.edit().putString("events",a.toString()).commit())
    }
    fun audit(c: Context,e: RemoteEvaluation) {
        val p=prefs(c);val previous=JSONObject(p.getString("phases","{}")!!);val phases=JSONObject();val now=System.currentTimeMillis();val intervals=JSONObject(p.getString("usage_intervals","{}")!!)
        for(s in e.modes?.states?:emptyList()) {
            val key=s.phase+":"+(s.window?.startAtMs?:0);val old=previous.optString(s.instance.id)
            phases.put(s.instance.id,key)
            val history=intervals.optJSONArray(s.instance.id)?:JSONArray()
            val last=if(history.length()>0)history.getJSONObject(history.length()-1)else null
            if(last!=null&&!last.has("endAtMs")&&(s.phase!="active"||last.optLong("windowStart")!=s.window?.startAtMs)) {
                last.put("endAtMs",minOf(now,last.getLong("windowEnd")))
            }
            if(s.phase=="active"&&(last==null||last.has("endAtMs")))history.put(JSONObject().put("startAtMs",maxOf(now,s.window!!.startAtMs)).put("windowStart",s.window.startAtMs).put("windowEnd",minOf(s.window.endAtMs,s.instance.expiresAtMs)))
            intervals.put(s.instance.id,JSONArray((0 until history.length()).map{history.getJSONObject(it)}.takeLast(64)))
            if(key!=old){
                val kind=when(s.phase){"active"->if(old.startsWith("paused:"))"mode_resumed" else "mode_activated";"paused"->"mode_paused";"expired","stopped","disabled"->"mode_ended";else->null}
                if(kind!=null&&(s.phase in setOf("active","paused")||(old.startsWith("active:")||old.startsWith("paused:"))))event(c,kind,mapOf("instanceId" to s.instance.id),"mode-${s.instance.id}-${s.phase}-${s.window?.startAtMs?:old.substringAfter(':',"0")}-${RemoteStore.revision(c)}")
                if(s.phase=="active")event(c,"schedule_fired",mapOf("instanceId" to s.instance.id),"fire-${s.instance.id}-${s.window?.startAtMs?:0}")
            }
        }
        val oldBlocks=JSONObject(p.getString("blocks","{}")!!);val newBlocks=JSONObject(e.activeBlocks)
        for((pkg,end) in e.activeBlocks)if(!oldBlocks.has(pkg))event(c,"app_block_added",mapOf("packageName" to pkg))
        for(pkg in oldBlocks.keys().asSequence().toList())if(!newBlocks.has(pkg)&&oldBlocks.optLong(pkg)<=System.currentTimeMillis())event(c,"app_block_expired",mapOf("packageName" to pkg))
        val oldExceptions=JSONObject(p.getString("exceptions","{}")!!);val active=JSONObject()
        for(x in e.modes?.exceptions?:emptyList()){active.put(x.id,x.untilMs);if(!oldExceptions.has(x.id))event(c,"temporary_exception_granted",mapOf("instanceId" to x.instanceId,"packageName" to x.packageName),"grant-${x.id}")}
        for(id in oldExceptions.keys().asSequence().toList())if(!active.has(id)&&oldExceptions.optLong(id)<=System.currentTimeMillis())event(c,"temporary_exception_expired",mapOf("reason" to id),"expire-$id")
        val ids=(e.modes?.states?:emptyList()).map{it.instance.id}.toSet();intervals.keys().asSequence().toList().filter{it !in ids}.forEach{intervals.remove(it)}
        check(p.edit().putString("usage_intervals",intervals.toString()).putString("phases",phases.toString()).putString("blocks",newBlocks.toString()).putString("exceptions",active.toString()).commit())
    }
    fun report(c: Context,e: RemoteEvaluation): JSONObject=JSONObject().put("schemaVersion",1).put("enabled",enabled(c)).put("actionsConsent",actionsConsent(c)).put("usageSharing",usageSharing(c))
        .put("eligiblePackages",JSONArray(eligible(c).toList())).put("states",JSONArray((e.modes?.states?:emptyList()).map{JSONObject().put("id",it.instance.id).put("phase",it.phase)}))
        .put("alarmSupportVersion",1).put("alarmConsent",com.mindful.android.alarms.RemoteAlarms.consent(c)).put("alarms",com.mindful.android.alarms.RemoteAlarms.reports(c)).put("actionErrors",JSONArray(DeviceModeActions.errors(c))).put("alarmStatus",alarmStatus(c,e))
    private fun alarmStatus(c: Context,e: RemoteEvaluation): String {
        val alarms=(e.modes?.states?:emptyList()).filter{it.phase=="active"&&it.instance.alarmAtMs!=null}
        if(alarms.isEmpty())return "none"
        return if(alarms.any{!prefs(c).getBoolean("alarm_handoff_${it.instance.id}",false)})"needs_user_confirmation" else "handed_to_clock"
    }
}
