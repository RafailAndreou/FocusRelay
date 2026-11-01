// Copyright 2026 Rafail Andreou. SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.alarms

/** Absolute one-shot deadlines; never replay old wake-up alarms after boot. */
object OwnedAlarmPolicy {
    val terminal=setOf("cancelled","dismissed","expired","missed")
    fun decision(state: String,cancel: Boolean,enabled: Boolean,ready: Boolean,trigger: Long,now: Long): String=when {
        cancel->"cancelled"
        state in terminal->state
        !enabled->"user_action_required"
        trigger<=now->if(state=="ringing"&&now-trigger<10*60000L)"ringing" else "missed"
        !ready->"failed"
        else->"scheduled"
    }
    fun mayRing(state: String,trigger: Long,now: Long)=state=="scheduled"&&now>=trigger&&now-trigger<=60000
}
