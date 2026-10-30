// Copyright 2026 Rafail Andreou. SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.modes

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.notification.Condition
import com.mindful.android.MainActivity
import com.mindful.android.remote.RemoteEvaluation
import org.json.JSONObject
import org.json.JSONArray

/** Value-based leases for global controls; DND contributes only our own automatic rule. */
object DeviceModeActions {
    fun errors(c: Context): List<String> {val a=JSONArray(DeviceModeStore.prefs(c).getString("action_errors","[]")!!);return (0 until a.length()).map{a.getString(it)}}
    fun hasJournal(c: Context)=JSONObject(DeviceModeStore.prefs(c).getString("settings_journal","{}")!!).length()>0
    fun permissions(c: Context): Map<String,Any> = mapOf("writeSettings" to Settings.System.canWrite(c),"notificationPolicy" to (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted,"dndSupported" to (Build.VERSION.SDK_INT>=29))
    private fun audio(c: Context)=c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private fun notification(c: Context)=c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private fun read(c: Context,key: String): Int=when(key){
        "brightness"->{check(Settings.System.canWrite(c)){"Grant Modify system settings"};if(Settings.System.getInt(c.contentResolver,Settings.System.SCREEN_BRIGHTNESS_MODE,0)!=Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)-1 else Settings.System.getInt(c.contentResolver,Settings.System.SCREEN_BRIGHTNESS)}
        "mediaVolume","ringtoneVolume"->{check(!audio(c).isVolumeFixed){"Device volume is fixed"};audio(c).getStreamVolume(if(key=="mediaVolume")AudioManager.STREAM_MUSIC else AudioManager.STREAM_RING)}
        "dnd"->{check(Build.VERSION.SDK_INT>=29){"Mode DND requires Android 10+"};check(notification(c).isNotificationPolicyAccessGranted){"Grant Notification Policy Access"};val p=DeviceModeStore.prefs(c);val id=p.getString("dnd_rule",null);if(id==null||notification(c).getAutomaticZenRule(id)?.isEnabled!=true)0 else if(Build.VERSION.SDK_INT>=35) { when(notification(c).getAutomaticZenRuleState(id)){Condition.STATE_TRUE->1;Condition.STATE_FALSE->0;else->error("DND rule state is unavailable")} } else if(p.getBoolean("dnd_requested",false))1 else 0}
        else->error("Unknown setting")
    }
    private fun target(c: Context,key: String,percent: Int): Int=when(key){
        "brightness"->(percent*255/100).coerceIn(13,255)
        "mediaVolume","ringtoneVolume"->{val max=audio(c).getStreamMaxVolume(if(key=="mediaVolume")AudioManager.STREAM_MUSIC else AudioManager.STREAM_RING);(percent*max/100).coerceIn(if(key=="ringtoneVolume")1 else 0,max)}
        else->percent
    }
    private fun write(c: Context,key: String,value: Int){
        when(key){
            "brightness"->check(Settings.System.putInt(c.contentResolver,Settings.System.SCREEN_BRIGHTNESS,value)){"Brightness change failed"}
            "mediaVolume","ringtoneVolume"->{val stream=if(key=="mediaVolume")AudioManager.STREAM_MUSIC else AudioManager.STREAM_RING
                // Never implicitly enter/leave silent mode. DND is a separate explicit action.
                if(key=="ringtoneVolume")check(audio(c).ringerMode==AudioManager.RINGER_MODE_NORMAL){"Leave silent/vibrate mode unchanged"}
                audio(c).setStreamVolume(stream,value,0);check(audio(c).getStreamVolume(stream)==value){"Volume request was not applied"}}
            "dnd"->{check(Build.VERSION.SDK_INT>=29);val p=DeviceModeStore.prefs(c);val n=notification(c)
                var id=p.getString("dnd_rule",null);if(id!=null&&n.getAutomaticZenRule(id)==null)id=null
                val uri=Uri.parse("condition://${c.packageName}/device-modes")
                if(id==null&&value==1){id=n.addAutomaticZenRule(AutomaticZenRule("FocusRelay device modes",null,ComponentName(c,MainActivity::class.java),uri,null,NotificationManager.INTERRUPTION_FILTER_ALARMS,true));check(id!=null){"Android refused the DND rule"};check(p.edit().putString("dnd_rule",id).commit())}
                // Persist intent before the framework side effect, allowing recovery after process death.
                check(p.edit().putBoolean("dnd_requested",value==1).commit())
                if(id!=null)n.setAutomaticZenRuleState(id,Condition(uri,"FocusRelay device mode",if(value==1)Condition.STATE_TRUE else Condition.STATE_FALSE))
            }
        }
    }
    @Synchronized fun reconcile(c: Context,evaluation: RemoteEvaluation) {
        val p=DeviceModeStore.prefs(c);var journal=JSONObject(p.getString("settings_journal","{}")!!);val errors=mutableListOf<String>()
        val requested=DeviceModeEngine.settingsRequests(evaluation.modes,DeviceModeStore.actionsConsent(c)&&DeviceModeStore.enabled(c))
        for(key in (journal.keys().asSequence().toList()+requested.keys).distinct()){
            try{
                val record=journal.optJSONObject(key)
                var old=record?.let{SettingLease(it.getInt("baseline"),it.getInt("lastWritten"),it.optBoolean("suppressed",false))}
                var observed=read(c,key);val value=requested[key]?.let{target(c,key,it)}
                // API 29–34 cannot read a rule's condition. Replay unfinished own-rule intent:
                // otherwise a crash between persisting intent and calling Android could look applied.
                if(key=="dnd"&&Build.VERSION.SDK_INT<35&&record?.has("pendingValue")==true&&old!=null){
                    write(c,key,value?:old.baseline);observed=read(c,key);old=old.copy(lastWritten=observed)
                }
                if(observed<0){
                    if(old!=null&&value!=null)journal.put(key,JSONObject().put("baseline",old.baseline).put("lastWritten",old.lastWritten).put("suppressed",true))else journal.remove(key)
                    check(p.edit().putString("settings_journal",journal.toString()).commit())
                    if(value!=null)errors.add("brightness: automatic brightness is left unchanged")
                    continue
                }
                if(old!=null)old=SettingOwnership.recover(old,observed,if(record?.has("pendingValue")==true)record.getInt("pendingValue")else null)
                val plan=SettingOwnership.plan(old,observed,value)
                if(plan.write!=null){
                    // Do not clear a restore lease until the restore succeeds. Journal target intent before writing.
                    val pending=plan.lease?:old
                    if(pending!=null)journal.put(key,JSONObject().put("baseline",pending.baseline).put("lastWritten",observed).put("pendingValue",plan.write).put("suppressed",pending.suppressed))
                    check(p.edit().putString("settings_journal",journal.toString()).commit())
                    write(c,key,plan.write)
                }
                if(plan.lease==null)journal.remove(key)else journal.put(key,JSONObject().put("baseline",plan.lease.baseline).put("lastWritten",plan.lease.lastWritten).put("suppressed",plan.lease.suppressed))
                check(p.edit().putString("settings_journal",journal.toString()).commit())
                if(plan.event!=null)DeviceModeStore.event(c,plan.event,mapOf("setting" to key))
            }catch(error: Exception){errors.add("$key: ${(error.message?:error.javaClass.simpleName).take(160)}")}
        }
        check(p.edit().putString("action_errors",JSONArray(errors.take(16)).toString()).commit())
    }
}
