// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
// SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.localrules

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.mindful.android.generics.SafeServiceConnection
import com.mindful.android.generics.ServiceBinder
import com.mindful.android.services.tracking.MindfulTrackerService
import com.mindful.android.helpers.storage.SharedPrefsHelper
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** One next-boundary alarm, with an in-process timer while the app remains alive. */
object LocalRuleScheduler {
    private val worker = Executors.newSingleThreadScheduledExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var timer: ScheduledFuture<*>? = null
    fun restore(context: Context, done: (() -> Unit)? = null) {
        val c = context.applicationContext
        worker.execute {
            try {
                timer?.cancel(false); timer = null
                val alarm = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val pi = PendingIntent.getBroadcast(c, 7410, Intent(c, LocalRuleAlarmReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                alarm.cancel(pi)
                val next = LocalRules.evaluation(c, true).nextAtMs
                if (next != null) {
                    try {
                        if (android.os.Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms())
                            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
                        else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
                    } catch (_: SecurityException) { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi) }
                    timer = worker.schedule({ restore(c) }, maxOf(1L, next - System.currentTimeMillis()), TimeUnit.MILLISECONDS)
                }
                main.post {
                    runCatching {
                        if (LocalRules.hasEnabled(c)) ContextCompat.startForegroundService(c,
                            Intent(c, MindfulTrackerService::class.java).setAction(ServiceBinder.ACTION_START_MINDFUL_SERVICE))
                        val connection = SafeServiceConnection(c, MindfulTrackerService::class.java)
                        connection.setOnConnectedCallback { it.refreshRemoteBlocks(); connection.unBindService() }
                        connection.bindService()
                    }.onFailure { SharedPrefsHelper.insertCrashLogToPrefs(c, it) }
                }
            } catch (e: Exception) { SharedPrefsHelper.insertCrashLogToPrefs(c, e) }
            finally { done?.invoke() }
        }
    }
}
class LocalRuleAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        LocalRuleScheduler.restore(context) { result.finish() }
    }
}

