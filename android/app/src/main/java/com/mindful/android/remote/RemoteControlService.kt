// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
// SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.mindful.android.MainActivity
import com.mindful.android.R
import com.mindful.android.generics.SafeServiceConnection
import com.mindful.android.helpers.device.PermissionsHelper
import com.mindful.android.services.tracking.MindfulTrackerService
import com.mindful.android.utils.Utils
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

class RemoteControlService : Service() {
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var disposed = false
    private lateinit var tracker: SafeServiceConnection<MindfulTrackerService>
    private var started = false
    private var lastPermissions: Pair<Boolean, Boolean>? = null
    override fun onCreate() {
        super.onCreate()
        tracker = SafeServiceConnection(this, MindfulTrackerService::class.java)
        RemoteStore.invalidateScheduler(this)
        RemoteScheduler.onTransition = { refreshTracker() }
    }
    override fun onBind(intent: Intent): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!RemoteStore.enabled(this)) { stopSelf(); return START_NOT_STICKY }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "Remote control connection", NotificationManager.IMPORTANCE_LOW))
        notifyStatus("Waiting for relay")
        RemoteScheduler.restore(this)
        if (!started) {
            started = true
            executor.scheduleWithFixedDelay({ sync() }, 0, 15, TimeUnit.SECONDS)
        } else if(intent?.action=="com.mindful.android.remote.SYNC_NOW") executor.execute{sync()}
        return START_STICKY
    }
    private fun notifyStatus(message: String) {
        val open = PendingIntent.getActivity(this, 7281, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_focus_mode)
            .setContentTitle("FocusRelay is enabled").setContentText(message)
            .setOngoing(true).setContentIntent(open).setSilent(true).build()
        startForeground(NOTIFICATION_ID, notification)
    }
    private fun refreshTracker() {
        handler.post {
            if (disposed) return@post
            try {
                if (RemoteStore.activeBlocks(this).isNotEmpty()) {
                    if (tracker.isActive) tracker.service?.refreshRemoteBlocks()
                    else {
                        tracker.setOnConnectedCallback { it.refreshRemoteBlocks() }
                        tracker.startAndBind()
                    }
                } else {
                    tracker.service?.refreshRemoteBlocks()
                    // Otherwise stopSelf leaves a disposed tracker bound here.
                    tracker.unBindService()
                }
            } catch(e: Exception) {
                RemoteStore.schedulerError(this, "Tracker unavailable: ${e.javaClass.simpleName}; reopen Mindful")
            }
        }
    }
    private fun sync() {
        if (disposed || !RemoteStore.enabled(this)) return
        var connection: HttpsURLConnection? = null
        try {
            val usage = PermissionsHelper.getAndAskUsageAccessPermission(this, false)
            val overlay = PermissionsHelper.getAndAskDisplayOverlayPermission(this, false)
            val evaluation = RemoteStore.evaluation(this)
            val activeBlocks = evaluation.activeBlocks
            val trackerAvailable = Utils.isServiceRunning(this, MindfulTrackerService::class.java)
            val trackerReady = activeBlocks.isEmpty() || trackerAvailable
            val schedulerReady = RemoteStore.schedulerReady(this)
            val errors = RemoteStore.scheduleErrors(this)
            // Permission loss cannot produce a successful acknowledgment.
            val payload = JSONObject().put("apps", JSONArray(RemoteStore.sharedApps(this)))
                .put("events", com.mindful.android.modes.DeviceModeStore.pendingEvents(this))
                .put("stoppedInstanceIds", JSONArray(com.mindful.android.modes.DeviceModeStore.stopped(this).toList()))
                .put("appliedRevision", if (usage && overlay && trackerReady && schedulerReady && errors.isEmpty()) RemoteStore.revision(this) else -1)
                .put("status", JSONObject().put("schemaVersion", 3)
                    .put("deviceModes", com.mindful.android.modes.DeviceModeStore.report(this,evaluation))
                    .put("usage", com.mindful.android.modes.ModeUsage.summary(this)).put("usageAccess", usage).put("overlay", overlay).put("enabled", true)
                    .put("trackerAvailable", trackerAvailable).put("serviceAvailable", !disposed).put("schedulerReady", schedulerReady)
                    .put("schedulerMode", RemoteStore.schedulerMode(this)).put("nextTransitionMs", RemoteStore.evaluation(this).nextTransitionMs ?: JSONObject.NULL)
                    .put("lastSuccessfulSyncMs", RemoteStore.lastSync(this)).put("clockSkewMs", RemoteStore.clockSkew(this))
                    .put("timeZone", java.util.TimeZone.getDefault().id).put("scheduleErrors", JSONArray(errors))
                    .put("enforcementErrors", JSONArray(RemoteStore.enforcementErrors(this)))
                    .put("activeBlocks", JSONArray(activeBlocks.map { (pkg, end) -> JSONObject().put("packageName", pkg).put("untilMs", end) })))
            connection = URL(RemoteStore.baseUrl(this) + "/device/sync").openConnection() as HttpsURLConnection
            connection.apply {
                connectTimeout = 10000; readTimeout = 10000; requestMethod = "POST"
                instanceFollowRedirects = false; doOutput = true
                setRequestProperty("Authorization", "Bearer " + RemoteStore.token(this@RemoteControlService))
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode != 200) throw IllegalStateException("Relay returned HTTP ${connection.responseCode}")
            val bytes = connection.inputStream.use { it.readBytesLimited(131072) }
            if (disposed || !RemoteStore.enabled(this)) return
            val responseJson=JSONObject(String(bytes, Charsets.UTF_8))
            com.mindful.android.modes.DeviceModeStore.acknowledgeEvents(this,responseJson.optJSONArray("receivedEventIds"))
            val changed = try { RemoteStore.applyState(this, responseJson) }
            catch(e: Exception) { RemoteStore.snapshotError(this, "Desired state rejected: ${e.message ?: e.javaClass.simpleName}"); throw e }
            val permissions = usage to overlay
            if (changed || lastPermissions != permissions) RemoteScheduler.restore(this)
            lastPermissions = permissions
            RemoteStore.synced(this)
            if (!usage || !overlay) RemoteStore.updateMessage(this, "Connected; blocking permissions missing")
            handler.post { if (!disposed) notifyStatus(if (usage && overlay) "Connected · checks every 15 seconds" else "Blocking permissions missing; open Mindful") }
        } catch (error: Exception) {
            if (!disposed) {
                val reason = if (error is IllegalStateException && error.message?.startsWith("Relay returned HTTP ") == true)
                    error.message else error.javaClass.simpleName
                RemoteStore.updateMessage(this, "Not connected: $reason")
                handler.post { if (!disposed) notifyStatus("Offline · existing timed blocks stay local") }
            }
        } finally { connection?.disconnect() }
    }
    override fun onDestroy() {
        disposed = true; RemoteScheduler.onTransition = null; executor.shutdownNow(); handler.removeCallbacksAndMessages(null)
        tracker.unBindService(); stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    companion object {
        private const val CHANNEL = "mindful_remote_connection"
        private const val NOTIFICATION_ID = 7281
        fun requestSync(context: Context) {
            val intent=Intent(context,RemoteControlService::class.java).setAction("com.mindful.android.remote.SYNC_NOW")
            if(Build.VERSION.SDK_INT>=26)context.startForegroundService(intent)else context.startService(intent)
        }
        fun start(context: Context) {
            val intent = Intent(context, RemoteControlService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }
    }
}

private fun java.io.InputStream.readBytesLimited(maximum: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(4096)
    while (true) {
        val size = read(buffer); if (size < 0) break
        require(output.size() + size <= maximum) { "Relay response too large" }
        output.write(buffer, 0, size)
    }
    return output.toByteArray()
}

