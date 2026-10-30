// Copyright 2026 Rafail Andreou. SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.modes

import org.junit.Assert.*
import org.junit.Test

class UsageMeasurementTest {
    @org.junit.Test fun activeIntervalsExcludePausesAndMergeOverlaps() {
        val events=listOf(UsagePoint(0,UsageMeasurementEngine.SCREEN_ON,null),UsagePoint(0,UsageMeasurementEngine.RESUME,"app"))
        val sample=UsageMeasurementEngine.measureIntervals(events,listOf(0L to 10000L,5000L to 15000L,25000L to 30000L))
        org.junit.Assert.assertEquals(20L,sample.appSeconds["app"])
        org.junit.Assert.assertEquals(20L,sample.screenSeconds)
    }

    @Test fun overlappingActivitiesAreNotDoubleCounted(){
        val events=listOf(UsagePoint(0,1,"a","one"),UsagePoint(1000,1,"a","two"),UsagePoint(2000,2,"a","one"),UsagePoint(3000,2,"a","two"))
        assertEquals(3L,UsageMeasurementEngine.measure(events,0,4000).appSeconds["a"])
    }
    @Test fun unknownScreenCoverageIsNullNotFabricatedZero(){
        assertNull(UsageMeasurementEngine.measure(listOf(UsagePoint(2000,1,"a")),0,4000).screenSeconds)
    }
    @Test fun screenOffClosesForegroundUsageAndClipsToWindow(){
        val events=listOf(UsagePoint(0,3,null),UsagePoint(1000,1,"a"),UsagePoint(5000,4,null))
        val x=UsageMeasurementEngine.measure(events,2000,8000)
        assertEquals(3L,x.appSeconds["a"]);assertEquals(3L,x.screenSeconds)
    }
}
