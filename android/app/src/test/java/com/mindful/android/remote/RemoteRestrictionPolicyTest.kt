// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
// SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.remote
import com.mindful.android.enums.RestrictionType
import org.junit.Assert.*
import org.junit.Test
class RemoteRestrictionPolicyTest {
    @Test fun endingRemoteRuleRevealsExistingFocusRatherThanUnblocking() {
        assertEquals(RestrictionType.REMOTE, RemoteRestrictionPolicy.type(true,true,false))
        assertEquals(RestrictionType.FOCUS, RemoteRestrictionPolicy.type(false,true,false))
    }
    @Test fun pauseOrDeleteDoesNotCancelBedtime() {
        assertEquals(RestrictionType.BEDTIME, RemoteRestrictionPolicy.type(false,false,true))
        assertEquals(RestrictionType.FOCUS, RemoteRestrictionPolicy.type(false,true,true))
    }
    @Test fun noRemoteRuleFallsThroughToExistingAppAndGroupChecks() {
        assertNull(RemoteRestrictionPolicy.type(false,false,false))
    }
}

