// Copyright 2026 Rafail Andreou. SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.modes

import org.junit.Assert.*
import org.junit.Test

class SettingOwnershipTest {
    @Test fun baselineIsCapturedAndRestoredOnlyWhenLastOwnerEnds(){
        val start=SettingOwnership.plan(null,80,20);assertEquals(80,start.lease!!.baseline);assertEquals(20,start.write)
        val overlap=SettingOwnership.plan(start.lease,20,30);assertEquals(30,overlap.write);assertEquals(80,overlap.lease!!.baseline)
        val finish=SettingOwnership.plan(overlap.lease,30,null);assertEquals(80,finish.write);assertNull(finish.lease)
    }
    @Test fun manualChangeIsNeverOverwrittenOrRestored(){
        val old=SettingLease(80,20);val manual=SettingOwnership.plan(old,50,20)
        assertNull(manual.write);assertTrue(manual.lease!!.suppressed)
        assertNull(SettingOwnership.plan(manual.lease,50,10).write)
        assertNull(SettingOwnership.plan(manual.lease,50,null).write)
    }
    @Test fun evenAnInitiallyMatchingValueTracksManualOwnership(){
        val p=SettingOwnership.plan(null,20,20);assertNotNull(p.lease);assertNull(p.write)
        assertNull(SettingOwnership.plan(p.lease,40,20).write)
    }
    @Test fun crashBeforeFirstWriteDoesNotRestoreAnUnappliedValue(){
        val pending=SettingLease(80,80)
        val recovered=SettingOwnership.recover(pending,80,20)
        assertNull(SettingOwnership.plan(recovered,80,null).write)
    }
    @Test fun crashAfterWriteRestoresBaselineWhenExpired(){
        val pending=SettingLease(80,80);val recovered=SettingOwnership.recover(pending,20,20)
        assertEquals(80,SettingOwnership.plan(recovered,20,null).write)
    }
    @Test fun crashBeforeRestoreRetriesAndCrashAfterRestoreClearsJournal(){
        val lease=SettingLease(80,20)
        assertEquals(80,SettingOwnership.plan(SettingOwnership.recover(lease,20,80),20,null).write)
        assertNull(SettingOwnership.plan(SettingOwnership.recover(lease,80,80),80,null).write)
    }
}
