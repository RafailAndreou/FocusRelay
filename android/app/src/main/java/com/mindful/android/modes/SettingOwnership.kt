// Copyright 2026 Rafail Andreou. SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.modes

/** Durable value-based ownership. The caller must persist before invoking any write. */
data class SettingLease(val baseline: Int, val lastWritten: Int, val suppressed: Boolean = false)
data class SettingPlan(val lease: SettingLease?, val write: Int? = null, val event: String? = null)
object SettingOwnership {
    fun recover(lease: SettingLease, observed: Int, pending: Int?): SettingLease = if(pending!=null&&observed==pending)lease.copy(lastWritten=observed)else lease
    // Own DND rules have a hidden automatic trigger even while a user snoozes them.
    // Release our trigger when the final owner ends; never change global DND policy.
    fun planOwnRule(existing: SettingLease?, observed: Int, requested: Int?): SettingPlan =
        if(existing!=null&&requested==null&&existing.baseline==0)SettingPlan(null,0,"device_action_restored") else plan(existing,observed,requested)
    fun plan(existing: SettingLease?, observed: Int, requested: Int?): SettingPlan {
        if(existing==null){
            if(requested==null)return SettingPlan(null)
            return SettingPlan(SettingLease(observed,requested),if(observed==requested)null else requested,if(observed==requested)null else "device_action_applied")
        }
        if(existing.suppressed)return SettingPlan(if(requested==null)null else existing)
        if(observed!=existing.lastWritten)return SettingPlan(if(requested==null)null else existing.copy(suppressed=true),event="manual_override")
        if(requested==null)return SettingPlan(null,if(observed!=existing.baseline)existing.baseline else null,"device_action_restored")
        if(requested==observed)return SettingPlan(existing)
        return SettingPlan(existing.copy(lastWritten=requested),requested,"device_action_applied")
    }
}
