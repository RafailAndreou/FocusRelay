// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
// SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.localrules

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.mindful.android.helpers.device.PermissionsHelper
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.time.ZoneId
import java.util.UUID
import androidx.core.graphics.drawable.toBitmap

class LocalRuleMethods(private val activity: Activity) {
    fun handle(call: MethodCall, result: MethodChannel.Result) {
        try {
            if (call.method == "localApps") {
                result.success(com.mindful.android.remote.RemoteStore.availableApps(activity, true).map { app ->
                    val icon = runCatching {
                        val out = java.io.ByteArrayOutputStream()
                        activity.packageManager.getApplicationIcon(app.getValue("packageName")).toBitmap(64, 64)
                            .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                        out.toByteArray()
                    }.getOrNull()
                    mapOf("packageName" to app["packageName"], "label" to app["label"], "icon" to icon)
                }); return
            }
            if (call.method == "localState") {
                val state = LocalRules.state(activity).toMutableMap()
                state["usagePermission"] = PermissionsHelper.getAndAskUsageAccessPermission(activity, false)
                state["overlayPermission"] = PermissionsHelper.getAndAskDisplayOverlayPermission(activity, false)
                state["exactAlarms"] = com.mindful.android.remote.RemoteScheduler.exactAllowed(activity)
                result.success(state); return
            }
            if (call.method == "localPermission") {
                Handler(Looper.getMainLooper()).post {
                    when (call.argument<String>("kind")) {
                        "usage" -> PermissionsHelper.getAndAskUsageAccessPermission(activity, true)
                        "overlay" -> PermissionsHelper.getAndAskDisplayOverlayPermission(activity, true)
                        "alarms" -> PermissionsHelper.getAndAskExactAlarmPermission(activity, true)
                        "battery" -> PermissionsHelper.getAndAskIgnoreBatteryOptimizationPermission(activity, true)
                    }
                    result.success(true)
                }
                return
            }
            when (call.method) {
                "saveLocalRule", "quickStart" -> {
                    require(PermissionsHelper.getAndAskUsageAccessPermission(activity, false) &&
                        PermissionsHelper.getAndAskDisplayOverlayPermission(activity, false)) { "Enable Usage access and Display over other apps in Settings first" }
                    if (call.method == "saveLocalRule") LocalRules.save(activity, call.argument<Map<String, Any?>>("rule") ?: emptyMap<String, Any>())
                    else {
                        val now = System.currentTimeMillis()
                        val minutes = call.argument<Int>("minutes") ?: 30
                        val packages = call.argument<List<String>>("packages") ?: emptyList()
                        LocalRules.configureQuick(activity, packages.toSet(), minutes)
                        LocalRules.save(activity, mapOf("id" to UUID.randomUUID().toString(), "name" to "Quick block",
                            "kind" to "quick", "packages" to packages, "minutes" to minutes,
                            "zone" to ZoneId.systemDefault().id, "startAtMs" to now, "endAtMs" to now + minutes * 60000L))
                    }
                }
                "deleteLocalRule" -> LocalRules.delete(activity, call.argument<String>("id") ?: "")
                "toggleLocalRule" -> LocalRules.toggle(activity, call.argument<String>("id") ?: "", call.argument<Boolean>("enabled") == true)
                "saveQuickConfig" -> LocalRules.configureQuick(activity,
                    (call.argument<List<String>>("packages") ?: emptyList()).toSet(), call.argument<Int>("minutes") ?: 30)
                else -> { result.notImplemented(); return }
            }
            LocalRuleScheduler.restore(activity)
            result.success(true)
        } catch (e: Exception) { result.error("LOCAL_RULE", e.message ?: "Could not save rule", null) }
    }
}

