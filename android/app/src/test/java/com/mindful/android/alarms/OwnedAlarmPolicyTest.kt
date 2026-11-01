// Copyright 2026 Rafail Andreou. SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.alarms
import org.junit.Assert.*
import org.junit.Test

class OwnedAlarmPolicyTest {
    @Test fun syncedRequestSchedulesWithoutUi(){assertEquals("scheduled",OwnedAlarmPolicy.decision("requested",false,true,true,10000,1000))}
    @Test fun permissionMissingFailsExplicitly(){assertEquals("failed",OwnedAlarmPolicy.decision("requested",false,true,false,10000,1000))}
    @Test fun noConsentRequiresLocalAction(){assertEquals("user_action_required",OwnedAlarmPolicy.decision("requested",false,false,true,10000,1000))}
    @Test fun futureAlarmSurvivesReboot(){assertEquals("scheduled",OwnedAlarmPolicy.decision("scheduled",false,true,true,10000,1000))}
    @Test fun overdueRebootNeverReplaysAlarm(){assertEquals("missed",OwnedAlarmPolicy.decision("scheduled",false,true,true,1000,10000))}
    @Test fun duplicateReceiverNeverStartsRingingTwice(){assertTrue(OwnedAlarmPolicy.mayRing("scheduled",1000,1001));assertFalse(OwnedAlarmPolicy.mayRing("ringing",1000,1001))}
    @Test fun staleBroadcastIsIgnored(){assertFalse(OwnedAlarmPolicy.mayRing("scheduled",1000,62000))}
    @Test fun earlyBroadcastIsIgnored(){assertFalse(OwnedAlarmPolicy.mayRing("scheduled",1000,999))}
    @Test fun cancelBeforeAndDuringRinging(){for(s in listOf("scheduled","ringing"))assertEquals("cancelled",OwnedAlarmPolicy.decision(s,true,true,true,1000,1001))}
    @Test fun dismissAndCancelAreDurableTombstones(){for(s in listOf("dismissed","cancelled"))assertEquals(s,OwnedAlarmPolicy.decision(s,false,true,true,10000,1000))}
    @Test fun ringingHasBoundedLifetime(){assertEquals("missed",OwnedAlarmPolicy.decision("ringing",false,true,true,1000,601001))}
}
