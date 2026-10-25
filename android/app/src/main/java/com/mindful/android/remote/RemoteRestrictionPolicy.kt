// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
// SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.remote
import com.mindful.android.enums.RestrictionType
/** Selects a display reason; never mutates or cancels upstream restriction state. */
object RemoteRestrictionPolicy {
    fun type(remote: Boolean, focus: Boolean, bedtime: Boolean): RestrictionType? = when {
        remote -> RestrictionType.REMOTE
        focus -> RestrictionType.FOCUS
        bedtime -> RestrictionType.BEDTIME
        else -> null // RestrictionManager continues its existing cached/timer/group checks.
    }
}

