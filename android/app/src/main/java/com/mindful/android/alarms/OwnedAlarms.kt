// Copyright 2026 Rafail Andreou. SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.alarms

import android.app.*
import android.content.*
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import com.mindful.android.MainActivity
import com.mindful.android.remote.RemoteScheduler
import org.json.JSONObject
import org.json.JSONArray

/** Owns only explicitly consented FocusRelay alarms, never another Clock app's alarms. */
object OwnedAlarms {
    const val CHANNEL="focusrelay_wake_alarm_v1"
    private fun prefs(c: Context)=c.getSharedPreferences("focusrelay_owned_alarms_v1",Context.MODE_PRIVATE)
    fun enabled(c: Context)=prefs(c).getBoolean("enabled",false)
    private fun records(c: Context)=JSONObject(prefs(c).getString("records","{}")!!)
    private fun save(c: Context,all: JSONObject){check(prefs(c).edit().putString("records",all.toString()).commit()){"Cannot persist alarm schedule"}}
    fun channel(c: Context){if(Build.VERSION.SDK_INT>=26)(c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(CHANNEL,"Wake-up alarms",NotificationManager.IMPORTANCE_HIGH).apply{description="FocusRelay alarm ringing and dismiss controls";setSound(null,null)})}
    fun readiness(c: Context): String? {
        channel(c)
        if(!RemoteScheduler.exactAllowed(c))return "Grant Alarms & reminders access"
        val nm=c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if(!nm.areNotificationsEnabled())return "Allow FocusRelay notifications for dismiss controls"
        if(Build.VERSION.SDK_INT>=26&&nm.getNotificationChannel(CHANNEL)?.importance==NotificationManager.IMPORTANCE_NONE)return "Enable the Wake-up alarms notification channel"
        if((c.getSystemService(Context.AUDIO_SERVICE) as AudioManager).getStreamVolume(AudioManager.STREAM_ALARM)==0)return "Alarm volume is muted; increase it locally"
        return null
    }
    private fun fire(c: Context,id: String)=PendingIntent.getBroadcast(c,0,Intent(c,OwnedAlarmReceiver::class.java).setAction("RING").setData(Uri.parse("focusrelay://alarm/"+id)),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun manager(c: Context)=c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    fun show(c: Context)=PendingIntent.getActivity(c,3821,Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    @Synchronized fun setup(c: Context,value: Boolean){
        if(value)require(readiness(c)==null){readiness(c)?:"Alarm permissions missing"}
        check(prefs(c).edit().putBoolean("enabled",value).commit())
        if(!value){val all=records(c);for(id in all.keys().asSequence().toList()){
            val a=all.getJSONObject(id);manager(c).cancel(fire(c,id));if(a.optString("state") !in OwnedAlarmPolicy.terminal)a.put("state","cancelled").put("updatedAt",System.currentTimeMillis())
        };save(c,all);c.stopService(Intent(c,WakeAlarmService::class.java))}
    }
    @Synchronized fun reconcile(c: Context,d: JSONObject): JSONObject {
        val all=records(c);val id=d.getString("id");val a=all.optJSONObject(id)?:JSONObject(d.toString()).put("state","requested")
        val now=System.currentTimeMillis();val issue=readiness(c)
        val observedState=if(a.optString("state")=="ringing"&&!WakeAlarmService.isRinging(id))"missed"else a.optString("state")
        val next=OwnedAlarmPolicy.decision(observedState,d.has("cancelRequestedAt"),enabled(c),issue==null,a.getLong("triggerAtMs"),now)
        if(next!="scheduled"&&next!="ringing")manager(c).cancel(fire(c,id))
        if(next=="cancelled")WakeAlarmService.stopIfRinging(c,id)
        if(next=="scheduled")try{
            // Persist intent before registration; replaying this stable PendingIntent replaces it.
            a.put("state","requested");all.put(id,a);save(c,all)
            manager(c).setAlarmClock(AlarmManager.AlarmClockInfo(a.getLong("triggerAtMs"),show(c)),fire(c,id))
            a.put("state","scheduled").remove("error")
        }catch(e:Exception){a.put("state","failed").put("error",(e.message?:"Exact alarm scheduling failed").take(160))}
        else {a.put("state",next);if(next=="failed"||next=="user_action_required")a.put("error",issue?:"Enable automatic alarms locally")else a.remove("error")}
        a.put("updatedAt",maxOf(now,d.optLong("cancelRequestedAt")))
        all.put(id,a)
        val stale=all.keys().asSequence().toList().filter{all.getJSONObject(it).optLong("triggerAtMs")<now-7*86400000L&&it!=id};stale.forEach{all.remove(it)}
        require(all.length()<=64){"Local alarm metadata limit reached"};save(c,all);return JSONObject(a.toString())
    }
    @Synchronized fun get(c: Context,id: String): JSONObject?=records(c).optJSONObject(id)?.let{JSONObject(it.toString())}
    @Synchronized fun mark(c: Context,id: String,state: String,error: String?=null){val all=records(c);val a=all.optJSONObject(id)?:return;if(state in OwnedAlarmPolicy.terminal)manager(c).cancel(fire(c,id));a.put("state",state).put("updatedAt",System.currentTimeMillis());if(error!=null)a.put("error",error.take(160));all.put(id,a);save(c,all)}
    @Synchronized fun restore(c: Context){val all=records(c);for(id in all.keys().asSequence().toList())reconcile(c,all.getJSONObject(id))}
}

class OwnedAlarmReceiver: BroadcastReceiver(){
    override fun onReceive(c: Context,i: Intent){
        val id=i.data?.lastPathSegment?:return
        if(i.action=="DISMISS"){OwnedAlarms.mark(c,id,"dismissed");WakeAlarmService.stopIfRinging(c,id);return}
        val a=OwnedAlarms.get(c,id)?:return
        if(!OwnedAlarms.enabled(c)||!OwnedAlarmPolicy.mayRing(a.optString("state"),a.getLong("triggerAtMs"),System.currentTimeMillis()))return
        try{val s=Intent(c,WakeAlarmService::class.java).putExtra("id",id);if(Build.VERSION.SDK_INT>=26)c.startForegroundService(s)else c.startService(s)}
        catch(e:Exception){OwnedAlarms.mark(c,id,"failed","Android refused alarm playback: ${e.javaClass.simpleName}")}
    }
}

class OwnedAlarmRecoveryReceiver: BroadcastReceiver(){
    override fun onReceive(c: Context,i: Intent){val p=goAsync();Thread{try{OwnedAlarms.restore(c)}finally{p.finish()}}.start()}
}
