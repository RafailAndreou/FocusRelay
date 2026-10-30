// Copyright 2026 Rafail Andreou. SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.modes

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.mindful.android.helpers.device.PermissionsHelper
import com.mindful.android.remote.RemoteStore
import org.json.JSONObject
import org.json.JSONArray
import java.time.Instant
import java.time.ZoneId

/** Event-derived estimates, not exact launch counters or an invasive activity stream. */
data class UsagePoint(val at: Long,val kind: Int,val packageName: String?,val activity: String? = null)
data class UsageMeasurement(val appSeconds: Map<String,Long>,val screenSeconds: Long?)
object UsageMeasurementEngine {
    // Kinds are independent of Android constants so edge cases can be tested on the JVM.
    const val RESUME=1;const val PAUSE=2;const val SCREEN_ON=3;const val SCREEN_OFF=4
    fun measure(events: List<UsagePoint>,start: Long,end: Long): UsageMeasurement {
        require(end>=start)
        val opened=mutableMapOf<String,MutableSet<String>>();val since=mutableMapOf<String,Long>();val total=mutableMapOf<String,Long>()
        var screen: Boolean?=null;var screenSince=start;var screenMs=0L;var screenKnownAtStart=false
        fun close(pkg: String,time: Long){since.remove(pkg)?.let{total[pkg]=(total[pkg]?:0)+maxOf(0,time-maxOf(start,it))};opened.remove(pkg)}
        for(e in events.sortedBy{it.at}){
            if(e.at>end)break
            if(e.at<=start&&(e.kind==SCREEN_ON||e.kind==SCREEN_OFF))screenKnownAtStart=true
            when(e.kind){
                SCREEN_ON->{if(screen!=true)screenSince=maxOf(start,e.at);screen=true}
                SCREEN_OFF->{if(screen==true)screenMs+=maxOf(0,e.at-maxOf(start,screenSince));screen=false;opened.keys.toList().forEach{close(it,e.at)}}
                RESUME->{val pkg=e.packageName?:continue;val acts=opened.getOrPut(pkg){mutableSetOf()};if(acts.isEmpty())since[pkg]=e.at;acts.add(e.activity?:"")}
                PAUSE->{val pkg=e.packageName?:continue;opened[pkg]?.let{it.remove(e.activity?:"");if(it.isEmpty())close(pkg,e.at)}}
            }
        }
        for(pkg in opened.keys.toList())close(pkg,end)
        if(screen==true)screenMs+=maxOf(0,end-maxOf(start,screenSince))
        return UsageMeasurement(total.mapValues{minOf((end-start)/1000,it.value/1000)},if(screenKnownAtStart)minOf((end-start)/1000,screenMs/1000)else null)
    }
}
object ModeUsage {
    @Volatile private var cached: JSONObject?=null
    @Volatile private var cachedAt=0L
    fun summary(c: Context): JSONObject {
        val now=System.currentTimeMillis()
        if(!DeviceModeStore.usageSharing(c))return JSONObject().put("available",false).put("reason","local_consent_missing").put("collectedAtMs",now)
        if(!PermissionsHelper.getAndAskUsageAccessPermission(c,false))return JSONObject().put("available",false).put("reason","usage_permission_missing").put("collectedAtMs",now)
        val allowed=RemoteStore.allowedPackages(c)
        val cache=cached
        if(cache!=null&&now>=cachedAt&&now-cachedAt<60000){
            // Never return a revoked app from a cached sample.
            val copy=JSONObject(cache.toString());prune(copy,allowed);return copy
        }
        return try{
            val zone=ZoneId.systemDefault();val day=Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
            val raw=RemoteStore.modeJson(c).optJSONArray("instances")?:JSONArray()
            val modes=JSONArray()
            val recent=(0 until raw.length()).map{raw.getJSONObject(it)}.filter{it.getJSONObject("timing").optString("type")=="once"&&it.getJSONObject("timing").getLong("startAtMs")<=now&&it.getLong("expiresAtMs")>now-7*MODE_DAY}.sortedByDescending{it.getLong("createdAtMs")}.take(4)
            for(i in recent){val t=i.getJSONObject("timing");val start=t.getLong("startAtMs");val end=minOf(now,i.getLong("expiresAtMs"),i.optLong("stoppedAtMs",Long.MAX_VALUE));if(end>start)modes.put(sample(c,start,end,allowed).put("instanceId",i.getString("id")))}
            JSONObject().put("available",true).put("collectedAtMs",now).put("today",sample(c,day,now,allowed)).put("modes",modes).also{cached=it;cachedAt=now}
        }catch(_: Exception){JSONObject().put("available",false).put("reason","android_usage_unavailable").put("collectedAtMs",now)}
    }
    private fun prune(o: JSONObject,allowed: Set<String>){
        val intervals=mutableListOf<JSONObject>();o.optJSONObject("today")?.let{intervals.add(it)}
        val modes=o.optJSONArray("modes")?:JSONArray();for(n in 0 until modes.length())intervals.add(modes.getJSONObject(n))
        for(i in intervals){val apps=i.getJSONArray("apps");i.put("apps",JSONArray((0 until apps.length()).map{apps.getJSONObject(it)}.filter{it.getString("packageName") in allowed}));i.put("complete",false)}
    }
    private fun sample(c: Context,start: Long,end: Long,allowed: Set<String>): JSONObject {
        val manager=c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val data=manager.queryEvents(maxOf(0,start-MODE_DAY),end)?:throw IllegalStateException("No Android events")
        val points=mutableListOf<UsagePoint>()
        while(data.hasNextEvent()){
            require(points.size<100000){"Usage interval too large"};val e=UsageEvents.Event();data.getNextEvent(e)
            val kind=when(e.eventType){UsageEvents.Event.ACTIVITY_RESUMED->UsageMeasurementEngine.RESUME;UsageEvents.Event.ACTIVITY_PAUSED,UsageEvents.Event.ACTIVITY_STOPPED->UsageMeasurementEngine.PAUSE;UsageEvents.Event.SCREEN_INTERACTIVE->UsageMeasurementEngine.SCREEN_ON;UsageEvents.Event.SCREEN_NON_INTERACTIVE->UsageMeasurementEngine.SCREEN_OFF;else->0}
            if(kind!=0)points.add(UsagePoint(e.timeStamp,kind,e.packageName,e.className))
        }
        if(points.isEmpty())throw IllegalStateException("No Android event coverage")
        val measurement=UsageMeasurementEngine.measure(points,start,end)
        val apps=allowed.map{JSONObject().put("packageName",it).put("seconds",measurement.appSeconds[it]?:0L)}.sortedByDescending{it.getLong("seconds")}.take(50)
        return JSONObject().put("startAtMs",start).put("endAtMs",end).put("screenTimeSeconds",measurement.screenSeconds?:JSONObject.NULL).put("complete",allowed.size<=50).put("apps",JSONArray(apps))
    }
}
