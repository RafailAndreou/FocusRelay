// Copyright 2026 Rafail Andreou. SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.modes

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.Settings
import com.mindful.android.MainActivity
import com.mindful.android.remote.RemoteStore
import com.mindful.android.remote.RemoteScheduler
import com.mindful.android.remote.RemoteControlService
import com.mindful.android.services.tracking.MindfulTrackerService
import com.mindful.android.generics.SafeServiceConnection
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import org.json.JSONObject
import org.json.JSONArray
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import javax.net.ssl.HttpsURLConnection

class DeviceModeMethodHandler(private val activity: Activity) {
    private val main=Handler(Looper.getMainLooper())
    private fun value(v: Any?): Any?=when(v){null,JSONObject.NULL->null;is JSONObject->v.keys().asSequence().associateWith{value(v.get(it))};is JSONArray->(0 until v.length()).map{value(v.get(it))};else->v}
    private fun refresh(){
        RemoteScheduler.restore(activity)
        main.post{val tracker=SafeServiceConnection(activity,MindfulTrackerService::class.java);tracker.setOnConnectedCallback{it.refreshRemoteBlocks();tracker.unBindService()};tracker.bindService()}
    }
    fun handle(call: MethodCall,result: MethodChannel.Result) {
        try{
            when(call.method){
                "modeState"->{
                    val e=RemoteStore.evaluation(activity);val o=JSONObject().put("connected",RemoteStore.enabled(activity)).put("enabled",DeviceModeStore.enabled(activity)).put("actionsConsent",DeviceModeStore.actionsConsent(activity)).put("usageSharing",DeviceModeStore.usageSharing(activity))
                        .put("exclusions",JSONArray(DeviceModeStore.excluded(activity).toList())).put("eligiblePackages",JSONArray(DeviceModeStore.eligible(activity).toList())).put("apps",JSONArray(RemoteStore.sharedApps(activity))).put("deviceModes",RemoteStore.modeJson(activity)).put("nextTransitionMs",e.nextTransitionMs?:JSONObject.NULL)
                        .put("permissions",JSONObject(DeviceModeActions.permissions(activity))).put("errors",JSONArray(DeviceModeActions.errors(activity)))
                        .put("instances",JSONArray((e.modes?.states?:emptyList()).map{s->JSONObject().put("id",s.instance.id).put("name",s.instance.name).put("mode",s.instance.mode).put("phase",s.phase).put("endAtMs",s.window?.endAtMs?:s.instance.expiresAtMs).put("allowedPackages",JSONArray(s.instance.allowedPackages.toList())).put("blockedPackages",JSONArray(s.blocked.toList())).put("alarmAtMs",s.instance.alarmAtMs?:JSONObject.NULL)}))
                        .put("exceptions",JSONArray((e.modes?.exceptions?:emptyList()).map{x->JSONObject().put("id",x.id).put("instanceId",x.instanceId).put("packageName",x.packageName).put("untilMs",x.untilMs)}))
                    result.success(value(o));return
                }
                "modeSetup"->{DeviceModeStore.setup(activity,call.argument<Boolean>("enabled")==true,call.argument<Boolean>("actionsConsent")==true,call.argument<Boolean>("usageSharing")==true,(call.argument<List<String>>("exclusions")?:emptyList()).toSet());refresh()}
                "endModeLocally"->{DeviceModeStore.stop(activity,call.argument<String>("instanceId")?:"");DeviceModeRuntime.reconcile(activity,RemoteStore.evaluation(activity));refresh()}
                "emergencyEndModes"->{DeviceModeStore.emergencyStop(activity);DeviceModeRuntime.reconcile(activity,RemoteStore.evaluation(activity));refresh()}
                "modePermission"->{
                    val name=call.argument<String>("name");require(name in setOf("dnd","brightness"))
                    main.post{try{activity.startActivity(if(name=="dnd")Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)else Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,Uri.parse("package:${activity.packageName}")));result.success(true)}catch(e:Exception){result.error("MODE_PERMISSION",e.message,null)}};return
                }
                "confirmModeAlarm"->{
                    require(MainActivity.isVisible&&DeviceModeStore.actionsConsent(activity)){"Open Modes and consent to device actions first"}
                    val id=call.argument<String>("instanceId")?:"";val instance=RemoteStore.evaluation(activity).modes?.states?.firstOrNull{it.instance.id==id&&it.phase=="active"}?.instance?:error("Mode is not active")
                    val alarm=instance.alarmAtMs?:error("This mode has no alarm request");val now=System.currentTimeMillis();require(alarm>now&&alarm<=now+MODE_DAY){"Alarm request is no longer within the next 24 hours"}
                    val local=Instant.ofEpochMilli(alarm).atZone(ZoneId.systemDefault())
                    val intent=Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR,local.hour).putExtra(AlarmClock.EXTRA_MINUTES,local.minute).putExtra(AlarmClock.EXTRA_MESSAGE,"FocusRelay: ${instance.name}").putExtra(AlarmClock.EXTRA_SKIP_UI,false)
                    require(intent.resolveActivity(activity.packageManager)!=null){"No Clock app supports alarm creation"}
                    main.post{try{activity.startActivity(intent);DeviceModeStore.prefs(activity).edit().putBoolean("alarm_handoff_$id",true).commit();result.success(true)}catch(e:Exception){result.error("MODE_ALARM",e.message,null)}};return
                }
                "modeCommand"->{
                    require(RemoteStore.enabled(activity)&&DeviceModeStore.enabled(activity)){"Enable Device Modes locally first"}
                    val operation=call.argument<String>("operation")?:""
                    require(operation in setOf("create_device_mode","update_device_mode","delete_device_mode","activate_device_mode","pause_device_mode","resume_device_mode","stop_device_mode","grant_temporary_app_access","revoke_temporary_app_access","create_mode_sequence")){"Unsupported mode operation"}
                    val args=call.argument<Map<String,Any?>>("arguments")?:emptyMap()
                    val connection=URL(RemoteStore.baseUrl(activity)+"/device/modes").openConnection() as HttpsURLConnection
                    try{
                        connection.connectTimeout=10000;connection.readTimeout=10000;connection.instanceFollowRedirects=false;connection.requestMethod="POST";connection.doOutput=true
                        connection.setRequestProperty("Authorization","Bearer "+RemoteStore.token(activity));connection.setRequestProperty("Content-Type","application/json")
                        val bytes=JSONObject().put("operation",operation).put("arguments",JSONObject(args)).toString().toByteArray(Charsets.UTF_8);require(bytes.size<=131072)
                        connection.outputStream.use{it.write(bytes)}
                        if(connection.responseCode!=202)throw IllegalStateException("Mode command was not accepted (HTTP ${connection.responseCode}); local setup may not have synced yet")
                    }finally{connection.disconnect()}
                    main.post{runCatching{RemoteControlService.requestSync(activity)}}
                }
                else->{result.notImplemented();return}
            }
            result.success(true)
        }catch(e:Exception){result.error("DEVICE_MODE",e.message?:"Device mode action failed",null)}
    }
}
