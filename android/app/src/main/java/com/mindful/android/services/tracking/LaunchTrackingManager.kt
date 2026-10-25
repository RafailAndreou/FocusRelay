// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
package com.mindful.android.services.tracking

import android.app.Service.USAGE_STATS_SERVICE
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import androidx.annotation.WorkerThread
import com.mindful.android.receivers.AccessibilityReceiver
import com.mindful.android.receivers.DeviceLockUnlockReceiver
import com.mindful.android.MainActivity
import com.mindful.android.remote.RemoteStore
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit


class LaunchTrackingManager(
    private val context: Context,
    private val onNewAppLaunched: (packageName: String) -> Unit,
    private val dismissOverlay: () -> Unit,
    private val cancelReminders: () -> Unit,
) {
    companion object {
        private const val TAG = "Mindful.LaunchTrackingManager"

        // Interval for tracking app launches in milliseconds
        private const val TIMER_RATE: Long = 750
    }

    // Lifecycle replay and periodic polling must never mutate activity state concurrently.
    private val executorService: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val lockUnlockReceiver = DeviceLockUnlockReceiver { isUnlocked ->
        if (isUnlocked) onDeviceUnlocked() else onDeviceLocked()
    }
    // Accessibility window events can be delayed or describe our own overlay.
    // Treat them only as hints to re-query activity lifecycle events. A running
    // accessibility service must never disable the usage-access fallback.
    private val accessibilityReceiver = AccessibilityReceiver(
        onNewAppLaunched = { requestForegroundCheck() },
        onServiceStatusChanged = { requestForegroundCheck() },
    )

    private var periodicTaskHandle: ScheduledFuture<*>? = null
    private var usageStatsManager: UsageStatsManager? = null
    @Volatile private var isTrackingPaused: Boolean = false

    private var lastUsageQueryTimestamp = System.currentTimeMillis()
    private var lastLaunchedApp = ""
    private val history = ForegroundEventHistory()
    @Volatile private var screenActive = true
    private var needsReplay = true

    init {
        // Register receivers
        lockUnlockReceiver.register(context)
        accessibilityReceiver.register(context)

        // Start tracking
        onDeviceUnlocked()
    }


    @WorkerThread
    private fun onDeviceUnlocked() {
        if (executorService.isShutdown) return
        screenActive = true
        executorService.execute {
            if (periodicTaskHandle == null || periodicTaskHandle?.isCancelled == true) {
                periodicTaskHandle = executorService.scheduleWithFixedDelay(
                    { if (screenActive) querySafely() }, 0L, TIMER_RATE, TimeUnit.MILLISECONDS
                )
            }
            // Never replay a package cached before locking or opening Mindful.
            querySafely(force = true)
        }
    }

    @WorkerThread
    private fun onDeviceLocked() {
        screenActive = false
        if (executorService.isShutdown) return
        executorService.execute {
            periodicTaskHandle?.cancel(false)
            periodicTaskHandle = null
            needsReplay = true
            history.clear()
            lastLaunchedApp = ""
            cancelReminders.invoke()
            dismissOverlay.invoke()
        }
    }

    private fun requestForegroundCheck() {
        if (!executorService.isShutdown) executorService.execute {
            if (screenActive) querySafely()
        }
    }

    /**
     * Detect if any app is opened using usage states.
     * @param replay Reconstruct a fresh snapshot instead of appending old events.
     */
    @WorkerThread
    private fun findLaunchedApp(replay: Boolean = false, force: Boolean = false) {
        // Initialize usage states manager
        if (usageStatsManager == null) {
            usageStatsManager =
                context.getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        }

        val timeNow = System.currentTimeMillis()
        val reconstruct = replay || needsReplay || timeNow < lastUsageQueryTimestamp
        // Re-read a short overlapping window: some devices publish lifecycle
        // events after their timestamp has passed the previous query boundary.
        val start = if (reconstruct) timeNow - 6 * 60 * 60 * 1000L
            else maxOf(0L, lastUsageQueryTimestamp - ForegroundEventHistory.LOOKBACK_MS)
        if (reconstruct) history.clear()
        val usageEvents = usageStatsManager?.queryEvents(start, timeNow)
            ?: throw IllegalStateException("Usage events unavailable")
        val events = mutableListOf<ForegroundEventHistory.Event>()
        val event = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            val action = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> ForegroundEventHistory.Action.RESUME
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> ForegroundEventHistory.Action.PAUSE
                UsageEvents.Event.DEVICE_SHUTDOWN -> ForegroundEventHistory.Action.RESET
                else -> continue
            }
            events.add(ForegroundEventHistory.Event(event.timeStamp,
                event.packageName ?: "", event.className ?: "", action))
        }
        val observed = history.observe(events, maxOf(0L, timeNow - ForegroundEventHistory.LOOKBACK_MS))
        val current = if (MainActivity.isVisible) context.packageName else observed
        lastUsageQueryTimestamp = timeNow
        needsReplay = false
        if (lastLaunchedApp != current || force) invokeNewAppLaunched(current)
        Log.d(TAG, "findLaunchedApp: Opened app:$lastLaunchedApp")
    }

    private fun querySafely(replay: Boolean = false, force: Boolean = false) {
        try {
            findLaunchedApp(replay, force)
        } catch (error: Exception) {
            // An uncaught exception cancels all future ScheduledExecutor polls.
            needsReplay = true
            lastLaunchedApp = ""
            dismissOverlay.invoke()
            RemoteStore.updateEnforcement(context, "App detection failed: ${error.javaClass.simpleName}")
            Log.e(TAG, "Unable to query foreground activity", error)
        }
    }

    /**
     * Check and Invoke method when new app is launched and tracking is not paused.
     */
    private fun invokeNewAppLaunched(packageName: String) {
        lastLaunchedApp = packageName
        if (RemoteStore.enabled(context)) RemoteStore.updateEnforcement(context,
            "Detected app: ${packageName.ifEmpty { "none" }}")
        if (isTrackingPaused || !screenActive) return

        onNewAppLaunched.invoke(packageName)
    }

    fun reInvokeLastLaunchEvent() {
        if (!executorService.isShutdown) executorService.execute { if (screenActive) querySafely(force = true) }
    }

    /** Re-evaluate an already open app after a remote update or local expiry. */
    fun refreshRemoteBlocks() {
        if (!executorService.isShutdown) executorService.execute {
            if (screenActive) querySafely(force = true)
        }
    }

    /**
     * Pauses or resumes app launch tracking.
     *
     * @param shouldPause True to pause tracking, false to resume.
     */
    fun pauseResumeTracking(shouldPause: Boolean) {
        isTrackingPaused = shouldPause
        if (!shouldPause) reInvokeLastLaunchEvent()
    }

    /**
     * Detect if any app is opened in last 6 hours and it is still active.
     * Show overlay if that app is marked as distracting bedtime app.
     */
    fun detectActiveAppForBedtime() {
        if (!executorService.isShutdown) executorService.submit { querySafely(replay = true) }
    }

    fun dispose() {
        // Un-Register receivers
        lockUnlockReceiver.unRegister(context)
        accessibilityReceiver.unRegister(context)

        periodicTaskHandle?.cancel(true)
        executorService.shutdownNow()

        screenActive = false
        cancelReminders.invoke()
        dismissOverlay.invoke()
    }
}

