// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
/*
 *
 *  *
 *  *  * Copyright (c) 2024 Mindful (https://github.com/akaMrNagar/Mindful)
 *  *  * Author : Pawan Nagar (https://github.com/akaMrNagar)
 *  *  *
 *  *  * This source code is licensed under the GPL-2.0 license license found in the
 *  *  * LICENSE file in the root directory of this source tree.
 *  *
 *
 */
package com.mindful.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.mindful.android.helpers.AlarmTasksSchedulingHelper.scheduleMidnightResetTask
import com.mindful.android.helpers.device.NotificationHelper
import com.mindful.android.helpers.storage.SharedPrefsHelper
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.StandardMethodCodec
import com.mindful.android.remote.RemoteMethodHandler
import com.mindful.android.remote.RemoteStore
import com.mindful.android.remote.RemoteControlService

class MainActivity : FlutterFragmentActivity() {
    private lateinit var fgMethodCallHandler: FgMethodCallHandler
    private lateinit var vpnPermissionLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {

        // Store uncaught exceptions
        Thread.setDefaultUncaughtExceptionHandler { _: Thread?, exception: Throwable ->
            SharedPrefsHelper.insertCrashLogToPrefs(
                this, exception
            )
        }

        // Register notification channels
        NotificationHelper.registerNotificationChannels(this)

        // Register VPN permission launcher
        vpnPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { }

        // initialize method channel and bind to services
        fgMethodCallHandler = FgMethodCallHandler(
            context = this,
            activity = this,
            vpnPermLauncher = vpnPermissionLauncher
        )

        // Schedule midnight 12 task if already not scheduled
        scheduleMidnightResetTask(this, true)
        super.onCreate(savedInstanceState)
    }


    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        val remoteMessenger = flutterEngine.dartExecutor.binaryMessenger
        MethodChannel(remoteMessenger, "com.mindful.android.remote",
            StandardMethodCodec.INSTANCE, remoteMessenger.makeBackgroundTaskQueue())
            .setMethodCallHandler(RemoteMethodHandler(this))
        val methodChannel = MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            AppConstants.FLUTTER_METHOD_CHANNEL_FG
        )
        methodChannel.setMethodCallHandler(fgMethodCallHandler)

        // Get the self start status
        val isSelfStart =
            intent.getBooleanExtra(AppConstants.INTENT_EXTRA_IS_SELF_RESTART, false)

        // Update self start status on flutter side
        methodChannel.invokeMethod("updateSelfStartStatus", isSelfStart)
        super.configureFlutterEngine(flutterEngine)
    }


    override fun onStart() {
        super.onStart()
        com.mindful.android.localrules.LocalRuleScheduler.restore(this)
        com.mindful.android.remote.RemoteScheduler.restore(this)
        if (RemoteStore.enabled(this)) runCatching { RemoteControlService.start(this) }
            .onFailure { RemoteStore.updateMessage(this, "Open remote control to reconnect") }
    }

    override fun onResume() {
        super.onResume()
        isVisible = true
    }

    override fun onPause() {
        isVisible = false
        super.onPause()
    }

    override fun onDestroy() {
        fgMethodCallHandler.dispose()
        super.onDestroy()
    }

    companion object {
        @Volatile var isVisible = false
            private set
        private const val TAG = "Mindful.MainActivity"
    }
}

