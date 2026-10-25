// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
// SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.remote

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** One next-transition alarm plus a one-shot in-process timer, independent of HTTPS sync. */
object RemoteScheduler {
    private val worker = Executors.newSingleThreadScheduledExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var timer: ScheduledFuture<*>? = null
    @Volatile var onTransition: (() -> Unit)? = null
    fun exactAllowed(context: Context): Boolean = Build.VERSION.SDK_INT < 31 || (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    fun restore(context: Context, completion: (() -> Unit)? = null) {
        val app = context.applicationContext
        worker.execute {
            try { reconcile(app) }
            catch(e: Exception) { RemoteStore.schedulerError(app, "Local scheduler failed: ${e.javaClass.simpleName}") }
            finally { completion?.invoke() }
        }
    }
    private fun reconcile(context: Context) {
        timer?.cancel(false); timer = null
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, RemoteAlarmReceiver::class.java).setAction("com.mindful.android.remote.TRANSITION")
        val pending = PendingIntent.getBroadcast(context, 7282, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        alarm.cancel(pending)
        RemoteStore.invalidateEvaluation()
        if (!RemoteStore.enabled(context)) return
        val revision = RemoteStore.revision(context)
        val evaluation = RemoteStore.evaluation(context)
        val next = evaluation.nextTransitionMs
        var mode = "none"
        if (next != null) {
            mode = if (exactAllowed(context)) "exact" else "inexact"
            try {
                if(mode == "exact") alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
                else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
            } catch(e: SecurityException) {
                // Access can be revoked between the readiness check and the API call.
                mode = "inexact"
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
            }
            timer = worker.schedule({ restore(context) }, maxOf(1, next - System.currentTimeMillis()), TimeUnit.MILLISECONDS)
        }
        // A concurrent desired-state update must never inherit readiness for an old revision.
        if (RemoteStore.revision(context) == revision) RemoteStore.schedulerPrepared(context, mode, revision)
        main.post {
            if (!RemoteStore.enabled(context)) return@post
            val listener = onTransition
            if (listener != null) listener()
            else runCatching { RemoteControlService.start(context) }.onFailure {
                RemoteStore.schedulerError(context, "Android deferred remote service start; reopen Mindful")
            }
        }
    }
}

class RemoteAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        RemoteScheduler.restore(context) { pending.finish() }
    }
}

