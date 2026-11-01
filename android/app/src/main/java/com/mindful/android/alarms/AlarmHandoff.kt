// Copyright 2026 Rafail Andreou. SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.alarms

import org.json.JSONObject

/** Durable side-effect boundary, injectable for crash/re-delivery tests. */
object AlarmHandoff {
    fun perform(a: JSONObject,now: ()->Long,persist: ()->Unit,handlerExists: ()->Boolean,launch: ()->Unit) {
        require(!a.optBoolean("launchAttempted")){"Already launched or uncertain; inspect Clock"}
        require(!a.has("cancelHandledAt")&&!a.has("cancelRequestedAt")){"Alarm cancellation requested"}
        require(handlerExists()){"No compatible Clock alarm handler"}
        a.put("launchAttempted",true).put("state","user_action_required").put("error","Handoff uncertain; inspect Clock before retrying").put("updatedAt",now())
        persist() // No external launch if this fails.
        launch()
        a.put("state","launched").put("updatedAt",now()).remove("error")
        persist()
    }
}
