// Copyright 2026 Rafail Andreou. SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.modes

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.provider.Telephony
import android.view.inputmethod.InputMethodManager
import com.mindful.android.helpers.device.ImpSystemAppsHelper

/** Conservative bulk-control exclusions. App labels are classification data, never instructions. */
object ModeSafety {
    val essentialNames = setOf("android", "com.android.settings", "com.android.systemui", "com.android.phone",
        "com.android.server.telecom", "com.android.permissioncontroller", "com.google.android.permissioncontroller",
        "com.google.android.packageinstaller", "com.android.packageinstaller", "com.samsung.android.dialer",
        "com.samsung.android.incallui", "com.google.android.dialer", "com.android.emergency")
    fun essential(context: Context): Set<String> {
        val result=essentialNames + context.packageName + ImpSystemAppsHelper.fetchImpApps(context.packageManager)
        val extras=mutableSetOf<String>()
        Telephony.Sms.getDefaultSmsPackage(context)?.let{extras.add(it)}
        runCatching{(context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).enabledInputMethodList.forEach{extras.add(it.packageName)}}
        return result+extras
    }
    fun eligibleName(packageName: String, label: String, system: Boolean, protected: Set<String>): Boolean {
        val s="$packageName $label".lowercase()
        return !system && packageName !in protected && !Regex("bank|auth|wallet|password|passkey|bitwarden|1password|keepass|protonpass|lastpass|secure|security|emergency|dialer|launcher|keyboard|clock|alarm").containsMatchIn(s)
    }
    fun eligible(context: Context, optedIn: Set<String>, excluded: Set<String>): Set<String> {
        val pm=context.packageManager;val protected=essential(context)+excluded
        return optedIn.filter { pkg -> runCatching {
            val info=pm.getApplicationInfo(pkg,0)
            val system=info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            eligibleName(pkg,info.loadLabel(pm).toString(),system,protected) && pm.getLaunchIntentForPackage(pkg)!=null
        }.getOrDefault(false) }.toSet()
    }
}
