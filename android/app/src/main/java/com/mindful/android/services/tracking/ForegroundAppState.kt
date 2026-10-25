// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
// SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.services.tracking

/** Reconstructed activity state, accessed only by the tracking executor. */
internal class ForegroundAppState {
    private data class Activity(val packageName: String, val className: String)
    private val resumed = linkedSetOf<Activity>()

    fun clear() = resumed.clear()

    fun copy(): ForegroundAppState = ForegroundAppState().also { it.resumed.addAll(resumed) }

    fun resume(packageName: String, className: String) {
        // A new app is authoritative; never fall back to a stale resumed
        // activity in another package after the newest app pauses.
        resumed.removeAll { it.packageName != packageName }
        val activity = Activity(packageName, className)
        resumed.remove(activity)
        resumed.add(activity)
    }

    fun pause(packageName: String, className: String) {
        resumed.remove(Activity(packageName, className))
    }

    val currentPackage: String get() = resumed.lastOrNull()?.packageName ?: ""
}

