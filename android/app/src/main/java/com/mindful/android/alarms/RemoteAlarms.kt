// Copyright 2026 Rafail Andreou. SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.alarms

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.mindful.android.MainActivity
import com.mindful.android.modes.DeviceModeStore
import com.mindful.android.remote.RemoteStore
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId

/** Metadata journal only. The Clock app owns playback and its actual alarm database. */
object RemoteAlarms {
    private fun prefs(c: Context)=c.getSharedPreferences("focusrelay_alarms_v1",Context.MODE_PRIVATE)
    fun consent(c: Context)=prefs(c).getBoolean("consent",false)
    fun consent(c: Context,enabled: Boolean){check(prefs(c).edit().putBoolean("consent",enabled).commit())}
    private fun save(c: Context,a: JSONArray){check(prefs(c).edit().putInt("schemaVersion",1).putString("metadata",a.toString()).commit()){"Cannot persist alarm metadata"}}
    private fun read(c: Context)=JSONArray(prefs(c).getString("metadata","[]")!!)
    @Synchronized fun reconcile(c: Context): JSONArray {
        val desired=RemoteStore.modeJson(c).optJSONArray("alarms")?:JSONArray();AlarmPolicy.validateAll(desired)
        val old=read(c);val byId=(0 until old.length()).map{old.getJSONObject(it)}.associateBy{it.getString("id")}
        val result=JSONArray();val now=System.currentTimeMillis()
        for(n in 0 until desired.length()){
            val d=desired.getJSONObject(n);val a=byId[d.getString("id")]?:JSONObject(d.toString()).put("state","user_action_required").put("updatedAt",now)
            if(d.has("cancelRequestedAt")&&!a.has("cancelHandledAt")){
                a.put("state",AlarmPolicy.cancel(a.optBoolean("launchAttempted"))).put("cancelHandledAt",d.getLong("cancelRequestedAt")).put("updatedAt",maxOf(now,d.getLong("cancelRequestedAt")))
                if(a.optBoolean("launchAttempted"))a.put("error","Cancel this alarm in Clock; system cancellation cannot be confirmed")
            }else if(a.getLong("triggerAtMs")<=now&&a.optString("state")!="cancelled"){
                a.put("state","expired").put("updatedAt",now).put("error","Requested time passed; ringing/dismissal is unknown")
            }
            result.put(a)
        }
        // Keep locally tracked handed-off alarms even if an older relay omits the extension.
        for(n in 0 until old.length()){val a=old.getJSONObject(n);if(!byId.containsKey(a.getString("id")))continue
            if((0 until result.length()).none{result.getJSONObject(it).getString("id")==a.getString("id")}&&result.length()<32)result.put(a)
        }
        save(c,result);return result
    }
    fun reports(c: Context): JSONArray {
        val a=reconcile(c);return JSONArray((0 until a.length()).map{val x=a.getJSONObject(it);JSONObject().put("id",x.getString("id")).put("state",x.getString("state")).put("updatedAt",x.getLong("updatedAt")).also{if(x.has("error"))it.put("error",x.getString("error"))}})
    }
    @Synchronized fun launch(activity: Activity,id: String,manage: Boolean=false) {
        require(MainActivity.isVisible){"Open FocusRelay first"}
        val all=reconcile(activity);val a=(0 until all.length()).map{all.getJSONObject(it)}.firstOrNull{it.getString("id")==id}?:error("Alarm not_found")
        if(manage){
            require(a.optBoolean("launchAttempted")){"Alarm was not handed to Clock"}
            val intent=Intent(AlarmClock.ACTION_SHOW_ALARMS)
            require(intent.resolveActivity(activity.packageManager)!=null){"No Clock app supports alarm management"}
            activity.startActivity(intent);return
        }
        require(RemoteStore.enabled(activity)&&DeviceModeStore.enabled(activity)&&DeviceModeStore.actionsConsent(activity)&&consent(activity)){"Enable system alarm requests and device actions locally"}
        require(!a.has("cancelHandledAt")&&!a.has("cancelRequestedAt")){"Alarm cancellation was requested"}
        require(!a.optBoolean("launchAttempted")){"Already launched or uncertain; inspect Clock to avoid a duplicate"}
        try {
            val (hour,minute)=AlarmPolicy.wallTime(a.getLong("triggerAtMs"),System.currentTimeMillis(),ZoneId.systemDefault())
            val intent=Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR,hour).putExtra(AlarmClock.EXTRA_MINUTES,minute)
                .putExtra(AlarmClock.EXTRA_MESSAGE,a.getString("label")+" [FR "+id.takeLast(8)+"]").putExtra(AlarmClock.EXTRA_SKIP_UI,a.getBoolean("skipUi"))
            AlarmHandoff.perform(a,{System.currentTimeMillis()},{save(activity,all)},
                {intent.resolveActivity(activity.packageManager)!=null},{activity.startActivity(intent)})
        }catch(e:Exception){a.put("state","failed").put("error",(e.message?:"Alarm handoff failed").take(160)).put("updatedAt",System.currentTimeMillis());save(activity,all);throw e}
    }
}
