// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
package com.mindful.android.services.tracking

import android.app.NotificationManager
import android.app.Service.NOTIFICATION_SERVICE
import android.content.Context
import android.content.Context.WINDOW_SERVICE
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.WindowInsets
import android.view.animation.DecelerateInterpolator
import android.animation.ValueAnimator
import android.widget.LinearLayout
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
import com.mindful.android.MainActivity
import com.mindful.android.R
import com.mindful.android.enums.RestrictionType
import com.mindful.android.remote.RemoteStore
import com.mindful.android.localrules.LocalRules
import com.mindful.android.helpers.device.NotificationHelper
import com.mindful.android.helpers.device.NotificationHelper.USAGE_REMINDERS_CHANNEL_ID
import com.mindful.android.helpers.storage.SharedPrefsHelper
import com.mindful.android.models.RestrictionState
import com.mindful.android.services.accessibility.MindfulAccessibilityService
import com.mindful.android.services.accessibility.MindfulAccessibilityService.Companion.ACTION_PERFORM_HOME_PRESS
import com.mindful.android.services.tracking.OverlayBuilder.getAppLabelAndIcon
import com.mindful.android.utils.AppUtils
import com.mindful.android.utils.DateTimeUtils
import com.mindful.android.utils.Utils


class OverlayManager(
    private val context: Context,
) {
    private val requests = OverlayRequestGate()
    private val ui = Handler(Looper.getMainLooper())
    private var remoteWindowEnd: Runnable? = null
    private val windowManager = context.getSystemService(WINDOW_SERVICE) as WindowManager
    private data class SheetTarget(val packageName: String, val state: RestrictionState)
    private val window = RegisteredOverlay<View, SheetTarget>(
        add = { windowManager.addView(it, sheetLayoutParams) },
        remove = { view ->
            try { windowManager.removeViewImmediate(view) }
            catch (error: IllegalArgumentException) {
                // Android may already have removed a service window during teardown.
                Log.w(TAG, "Overlay was already unregistered", error)
            }
        },
    )
    private val toastRequests = OverlayRequestGate()
    private var toastEnd: Runnable? = null
    private val toastWindow = RegisteredOverlay<View, Pair<String, Int>>(
        add = { windowManager.addView(it, toastLayoutParams) },
        remove = { view ->
            view.animate().cancel()
            try { windowManager.removeViewImmediate(view) }
            catch (_: IllegalArgumentException) { /* Already removed by Android. */ }
        },
    )
    private fun onUi(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else ui.post { block() }
    }

    fun dismissSheetOverlay() {
        val request = requests.next()
        onUi {
            if (requests.isCurrent(request)) removeSheetOverlay()
        }
    }

    private fun removeSheetOverlay() {
        remoteWindowEnd?.let { ui.removeCallbacks(it) }
        remoteWindowEnd = null
        window.clear()
    }

    /** One-shot expiry cleanup; it does not depend on a new foreground event. */
    private fun armRemoteWindowEnd() {
        remoteWindowEnd?.let { ui.removeCallbacks(it) }
        remoteWindowEnd = null
        val target = window.target ?: return
        if (window.closed || target.state.type !in setOf(RestrictionType.REMOTE, RestrictionType.LOCAL_RULE)) return
        val until = if (target.state.type == RestrictionType.LOCAL_RULE)
            LocalRules.evaluation(context).blocked[target.packageName]
            else RemoteStore.activeBlocks(context)[target.packageName]
        if (until == null) {
            window.clear()
            return
        }
        val task = Runnable { releaseRemovedRemoteBlock() }
        remoteWindowEnd = task
        ui.postDelayed(task, maxOf(1L, until - System.currentTimeMillis()))
    }

    /** Revoke a remote window independently of foreground queries or their success. */
    fun releaseRemovedRemoteBlock() {
        onUi {
            window.clearIf { target ->
                (target.state.type == RestrictionType.REMOTE && !RemoteStore.activeBlocks(context).containsKey(target.packageName)) ||
                (target.state.type == RestrictionType.LOCAL_RULE && !LocalRules.evaluation(context).blocked.containsKey(target.packageName)) }
            armRemoteWindowEnd()
        }
    }

    /** Terminal: late detector/reminder callbacks can never recreate this window. */
    fun dispose() {
        requests.close()
        toastRequests.close()
        onUi {
            toastEnd?.let { ui.removeCallbacks(it) }
            toastEnd = null
            remoteWindowEnd?.let { ui.removeCallbacks(it) }
            remoteWindowEnd = null
            try { window.close() } finally { toastWindow.close() }
        }
    }

    fun showSheetOverlay(
        packageName: String,
        restrictionState: RestrictionState,
        addReminderWithDelay: ((futureMinutes: Int) -> Unit)? = null,
    ) {
        val request = requests.next()
        onUi {
            if (!requests.isCurrent(request)) return@onUi
            if (MainActivity.isVisible || packageName == context.packageName) {
                removeSheetOverlay()
                return@onUi
            }
            runCatching {
                if (packageName in com.mindful.android.modes.ModeSafety.essential(context) || !haveOverlayPermission(context)) {
                    removeSheetOverlay()
                    return@onUi
                }
                val target = SheetTarget(packageName, restrictionState)
                val allowed = { requests.isCurrent(request) &&
                    (restrictionState.type != RestrictionType.REMOTE || RemoteStore.activeBlocks(context).containsKey(packageName)) &&
                    (restrictionState.type != RestrictionType.LOCAL_RULE || LocalRules.evaluation(context).blocked.containsKey(packageName)) }
                if (!allowed()) {
                    releaseRemovedRemoteBlock()
                    return@onUi
                }
                val sheetOverlay = window.show(target, allowed) {
                    OverlayBuilder.buildFullScreenOverlay(
                        context = context,
                        packageName = packageName,
                        state = restrictionState,
                        dismissOverlay = ::dismissSheetOverlay,
                        addReminderDelay = addReminderWithDelay,
                    )
                }
                armRemoteWindowEnd()
                if (sheetOverlay == null) return@onUi
                sheetOverlay.also {
                    LocalRules.recordBlock(context, packageName, restrictionState.groupName ?: restrictionState.type.name.lowercase().replace('_', ' '))
                    if (restrictionState.type == RestrictionType.REMOTE)
                        com.mindful.android.modes.DeviceModeStore.event(context,"blocked_app_opened",mapOf("packageName" to packageName))
                    if (restrictionState.type == RestrictionType.REMOTE)
                        RemoteStore.updateOverlayResult(context, "Blocking overlay shown: $packageName")
                    toastRequests.next()
                    toastEnd?.let { ui.removeCallbacks(it) }
                    toastEnd = null
                    toastWindow.clear()
                    Utils.vibrateDevice(context, 50L)

                    // Appearance never controls enforcement or dismissal.
                    if (animationsEnabled()) {
                        val sheet = sheetOverlay.findViewById<LinearLayout>(R.id.overlay_sheet)
                        sheet.alpha = 0f
                        sheet.translationY = 12f * context.resources.displayMetrics.density
                        sheet.animate().alpha(1f).translationY(0f)
                            .setInterpolator(DecelerateInterpolator()).setDuration(180).start()
                    }
                }
            }.getOrElse {
                if (restrictionState.type == RestrictionType.REMOTE)
                    RemoteStore.updateOverlayResult(context, "Blocking overlay failed: ${it.javaClass.simpleName}")
                SharedPrefsHelper.insertCrashLogToPrefs(context, it)
            }
        }
    }


    fun showToastOverlay(
        packageName: String,
        screenTimeUsedInMins: Int,
    ) {
        val request = toastRequests.next()
        onUi {
            if (!toastRequests.isCurrent(request) || window.closed || MainActivity.isVisible) return@onUi
            runCatching {
                if (!haveOverlayPermission(context)) return@runCatching
                val view = toastWindow.show(packageName to screenTimeUsedInMins,
                    allowed = { toastRequests.isCurrent(request) && !window.closed }) {
                    OverlayBuilder.buildToastOverlay(context, packageName, screenTimeUsedInMins)
                } ?: return@runCatching
                toastEnd?.let { ui.removeCallbacks(it) }
                if (animationsEnabled()) view.animate().alpha(1f).setDuration(180).start() else view.alpha = 1f
                val end = Runnable { if (toastRequests.isCurrent(request)) toastWindow.clear() }
                toastEnd = end
                ui.postDelayed(end, 5000L)
            }.getOrElse { SharedPrefsHelper.insertCrashLogToPrefs(context, it) }
        }
    }

    fun showNotification(
        packageName: String,
        screenTimeUsedInMins: Int,
    ) {
        // Get notification manager
        val notificationManager =
            context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Resolve app icon and label
        val (appName, appIcon) = getAppLabelAndIcon(context, packageName)

        val msg = context.getString(
            R.string.app_screen_time_usage_info,
            DateTimeUtils.minutesToTimeStr(screenTimeUsedInMins)
        )
        notificationManager.notify(
            302,
            NotificationCompat.Builder(
                context,
                USAGE_REMINDERS_CHANNEL_ID
            )
                .setSmallIcon(R.drawable.ic_mindful_notification)
                .setLargeIcon(appIcon.toBitmap())
                .setContentTitle(appName)
                .setContentText(msg)
                .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
                .setSound(null)
                .setAutoCancel(true)
                .setContentIntent(
                    AppUtils.getPendingIntentForMindfulUri(
                        context,
                        "com.mindful.android://open/appDashboard?package=$packageName"
                    )
                )
                .build()
        )

    }

    companion object {
        private const val TAG = "Mindful.OverlayManager"

        private fun animationsEnabled(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()

        private val sheetLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            // Never cover navigation/status bars, including gesture navigation.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setFitInsetsTypes(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                setFitInsetsIgnoringVisibility(true)
            }
        }

        private val toastLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            verticalMargin = 0.05f
        }


        private fun haveOverlayPermission(context: Context): Boolean {
            if (!Settings.canDrawOverlays(context)) {
                // Show notification
                NotificationHelper.pushAskOverlayPermissionNotification(context)

                // Go home if accessibility is running
                if (Utils.isServiceRunning(context, MindfulAccessibilityService::class.java)) {
                    val serviceIntent = Intent(
                        context.applicationContext,
                        MindfulAccessibilityService::class.java
                    ).setAction(ACTION_PERFORM_HOME_PRESS)

                    context.startService(serviceIntent)
                }

                Log.d(
                    TAG,
                    "checkOverlayPermission: Display overlay permission denied, returning"
                )
                return false
            } else {
                return true
            }
        }
    }
}

