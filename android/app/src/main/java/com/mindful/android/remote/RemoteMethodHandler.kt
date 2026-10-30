// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
// SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.remote

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.provider.Settings
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.mindful.android.helpers.device.PermissionsHelper
import com.mindful.android.generics.SafeServiceConnection
import com.mindful.android.services.tracking.MindfulTrackerService
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class RemoteMethodHandler(private val activity: Activity) : MethodChannel.MethodCallHandler {
    private val main = Handler(Looper.getMainLooper())

    private fun finishOnMain(result: MethodChannel.Result, action: () -> Unit) {
        main.post {
            try { action(); result.success(true) }
            catch (e: Exception) { result.error("REMOTE_CONTROL", e.message ?: "Remote control setup failed", null) }
        }
    }

    private fun refreshTracker() {
        val tracker = SafeServiceConnection(activity, MindfulTrackerService::class.java)
        tracker.setOnConnectedCallback { service -> service.refreshRemoteBlocks(); tracker.unBindService() }
        tracker.bindService()
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        try {
            when (call.method) {
                "status" -> result.success(RemoteStore.status(activity))
                "apps" -> result.success(RemoteStore.availableApps(activity, refresh = true))
                "connect" -> {
                    require(PermissionsHelper.getAndAskUsageAccessPermission(activity, false) && PermissionsHelper.getAndAskDisplayOverlayPermission(activity, false)) { "Grant Usage Access and Display over other apps first" }
                    RemoteStore.configure(activity, call.argument<String>("url") ?: "", call.argument<String>("token") ?: "")
                    RemoteStore.setAllowed(activity, (call.argument<List<String>>("packages") ?: emptyList()).toSet())
                    require(RemoteStore.allowedPackages(activity).isNotEmpty()) { "Select at least one app" }
                    RemoteStore.setEnabled(activity, true)
                    RemoteScheduler.restore(activity)
                    finishOnMain(result) {
                        activity.stopService(Intent(activity, RemoteControlService::class.java))
                        RemoteControlService.start(activity)
                    }
                }
                "updateApps" -> {
                    RemoteStore.setAllowed(activity, (call.argument<List<String>>("packages") ?: emptyList()).toSet())
                    RemoteScheduler.restore(activity)
                    finishOnMain(result) { refreshTracker() }
                }
                "disconnect" -> {
                    RemoteStore.disconnect(activity)
                    RemoteScheduler.restore(activity)
                    finishOnMain(result) {
                        activity.stopService(Intent(activity, RemoteControlService::class.java))
                        refreshTracker()
                    }
                }
                "exactAlarms" -> finishOnMain(result) {
                    if (Build.VERSION.SDK_INT >= 31 && !RemoteScheduler.exactAllowed(activity)) activity.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${activity.packageName}")))
                }
                "pauseRemote", "resumeRemote" -> {
                    require(RemoteStore.enabled(activity)) { "Connect remote control first" }
                    val body = JSONObject().put("operation", if(call.method == "pauseRemote") "pause_remote" else "resume_remote")
                        .put("targetType", call.argument<String>("targetType")).put("id", call.argument<String>("id"))
                    val connection = URL(RemoteStore.baseUrl(activity) + "/device/override").openConnection() as HttpsURLConnection
                    try {
                        connection.connectTimeout = 10000; connection.readTimeout = 10000
                        connection.requestMethod = "POST"; connection.instanceFollowRedirects = false; connection.doOutput = true
                        connection.setRequestProperty("Authorization", "Bearer " + RemoteStore.token(activity))
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                        require(connection.responseCode == 200) { "Cannot save override (HTTP ${connection.responseCode}); existing rules remain" }
                        val bytes = connection.inputStream.use { input ->
                            val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(4096)
                            while(true) { val n = input.read(buffer); if(n < 0) break; require(out.size()+n <= 131072); out.write(buffer,0,n) }; out.toByteArray()
                        }
                        RemoteStore.applyState(activity, JSONObject(String(bytes, Charsets.UTF_8)))
                        RemoteScheduler.restore(activity)
                        finishOnMain(result) { refreshTracker() }
                    } finally { connection.disconnect() }
                }
                else -> if(call.method in setOf("modeState","modeSetup","endModeLocally","emergencyEndModes","modePermission","confirmModeAlarm","modeCommand")) com.mindful.android.modes.DeviceModeMethodHandler(activity).handle(call,result) else com.mindful.android.localrules.LocalRuleMethods(activity).handle(call, result)
            }
        } catch (e: Exception) { result.error("REMOTE_CONTROL", e.message ?: "Remote control setup failed", null) }
    }
}

