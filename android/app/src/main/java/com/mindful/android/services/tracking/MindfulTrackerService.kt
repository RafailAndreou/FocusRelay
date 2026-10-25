// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
package com.mindful.android.services.tracking

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import com.mindful.android.remote.RemoteStore
import android.util.Log
import androidx.annotation.WorkerThread
import com.mindful.android.AppConstants
import com.mindful.android.R
import com.mindful.android.generics.ServiceBinder
import com.mindful.android.helpers.device.NotificationHelper
import com.mindful.android.helpers.storage.SharedPrefsHelper

class MindfulTrackerService : Service() {
    companion object {
        private const val TAG = "Mindful.MindfulTrackerService"
    }

    private val mBinder = ServiceBinder(this@MindfulTrackerService)

    private lateinit var overlayManager: OverlayManager
    private lateinit var reminderManager: ReminderManager

    private lateinit var restrictionManager: RestrictionManager
    val getRestrictionManager get() = restrictionManager

    private lateinit var launchTrackingManager: LaunchTrackingManager
    val getLaunchTrackingManager get() = launchTrackingManager
    private val remoteExpiryHandler = Handler(Looper.getMainLooper())
    @Volatile private var disposed = false
    private var hasBoundClients = false

    override fun onCreate() {
        overlayManager = OverlayManager(this)
        reminderManager = ReminderManager(overlayManager, ::onNewAppLaunch)
        restrictionManager = RestrictionManager(this, ::stopIfNoUsage)
        launchTrackingManager = LaunchTrackingManager(
            context = this,
            onNewAppLaunched = ::onNewAppLaunch,
            dismissOverlay = { overlayManager.dismissSheetOverlay() },
            cancelReminders = { reminderManager.cancelReminders() },
        )
        super.onCreate()
    }

    fun refreshRemoteBlocks() {
        if (disposed) return
        overlayManager.releaseRemovedRemoteBlock()
        launchTrackingManager.refreshRemoteBlocks()
        // Let the reevaluation remove expired overlays before disposing an idle tracker.
        remoteExpiryHandler.postDelayed({ if (!disposed) stopIfNoUsage() }, 600)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (intent?.action == ServiceBinder.ACTION_START_MINDFUL_SERVICE) {
            startFgService()
            refreshRemoteBlocks()
            return START_STICKY
        }

        stopIfNoUsage()
        return START_NOT_STICKY
    }

    private fun startFgService() {
        try {
            val notification = NotificationHelper.buildFgServiceNotification(
                this,
                getString(R.string.app_blocker_running_notification_info)
            )
            startForeground(AppConstants.TRACKER_SERVICE_NOTIFICATION_ID, notification)
            Log.d(TAG, "startFgService: TRACKER service started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "startFgService: Failed to start TRACKER service", e)
            SharedPrefsHelper.insertCrashLogToPrefs(this, e)
            stopIfNoUsage()
        }
    }

    fun onMidnightReset() {
        restrictionManager.resetCache()
        overlayManager.dismissSheetOverlay()
        val reminderAwaiting = reminderManager.cancelReminders()

        // Means app is active but timer is not over and now it is reset so re-launch same event again
        if (reminderAwaiting) launchTrackingManager.reInvokeLastLaunchEvent()
    }


    @WorkerThread
    private fun onNewAppLaunch(packageName: String) {
        if (disposed) return
        try {
            reminderManager.cancelReminders()
            // Empty foreground and Mindful itself are never blocking targets.
            if (packageName.isEmpty() || packageName == this.packageName) {
                overlayManager.dismissSheetOverlay()
                return
            }

            /// check current restrictions
            val currentOrFutureState = restrictionManager.isAppRestricted(packageName)
            Log.d(TAG, "onNewAppLaunch: $packageName's evaluated state => $currentOrFutureState")

            if (currentOrFutureState == null || currentOrFutureState.timeLeftMillis > 0L)
                overlayManager.dismissSheetOverlay()
            currentOrFutureState?.let {
                /// Already restricted
                if (it.timeLeftMillis <= 0L) {
                    overlayManager.showSheetOverlay(
                        packageName = packageName,
                        restrictionState = it,
                    )
                }
                /// Under limit but will be exhausted in some time
                else {
                    reminderManager.scheduleReminders(
                        packageName = packageName,
                        state = it,
                    )
                }
            }
        } catch (e: Exception) {
            SharedPrefsHelper.insertCrashLogToPrefs(this, e)
            Log.e(TAG, "onNewAppLaunch: Failed to process new app launch event", e)
        }
    }

    private fun stopIfNoUsage() {
        if (!disposed && restrictionManager.isIdle) {
            // A bound service is not destroyed by stopSelf. Keep it usable for
            // its other clients until the last client unbinds.
            if (hasBoundClients) {
                reminderManager.cancelReminders()
                overlayManager.dismissSheetOverlay()
                return
            }
            disposed = true
            overlayManager.dispose()
            remoteExpiryHandler.removeCallbacksAndMessages(null)
            Log.d(TAG, "Service no longer needed, stopping")
            launchTrackingManager.dispose()
            reminderManager.cancelReminders()
            overlayManager.dismissSheetOverlay()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        overlayManager.dispose()
        if (!disposed) {
            disposed = true
            remoteExpiryHandler.removeCallbacksAndMessages(null)
            launchTrackingManager.dispose()
            reminderManager.cancelReminders()
            overlayManager.dismissSheetOverlay()
        }
        Log.d(TAG, "onDestroy: TRACKER service destroyed successfully")
        super.onDestroy()
    }


    override fun onUnbind(intent: Intent?): Boolean {
        hasBoundClients = false
        stopIfNoUsage()
        return true
    }

    override fun onRebind(intent: Intent?) {
        hasBoundClients = true
        super.onRebind(intent)
    }

    override fun onBind(intent: Intent): IBinder? {
        return if (intent.action == ServiceBinder.ACTION_BIND_TO_MINDFUL) {
            hasBoundClients = true
            mBinder
        } else null
    }
}

