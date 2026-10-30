// Copyright 2026 Rafail Andreou. SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.alarms

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/** Pure validation: ACTION_SET_ALARM cannot express an arbitrary date or DST fold. */
object AlarmPolicy {
    fun validate(a: JSONObject) {
        require(a.getString("id").matches(Regex("alarm-[-A-Za-z0-9_]{1,64}")))
        for(k in listOf("triggerAtMs","createdAt","updatedAt")) {
            val v=a.get(k);require(v is Number && v.toDouble()==v.toLong().toDouble() && v.toLong()>=0)
        }
        require(a.getLong("triggerAtMs")%60000L==0L)
        val label=a.get("label");require(label is String && label.isNotBlank() && label.length<=80 && label.none{it<' '})
        require(a.get("skipUi") is Boolean)
        require(a.getJSONArray("daysOfWeek").length()==0){"Recurring alarms are unsupported in V1"}
        if(a.has("cancelRequestedAt")){val n=a.get("cancelRequestedAt");require(n is Number && n.toLong()>0 && n.toDouble()==n.toLong().toDouble())}
    }
    fun validateAll(a: JSONArray) {
        require(a.length()<=32);val ids=mutableSetOf<String>()
        for(n in 0 until a.length()){val item=a.getJSONObject(n);validate(item);require(ids.add(item.getString("id")))}
    }
    fun wallTime(trigger: Long,now: Long,zone: ZoneId): Pair<Int,Int> {
        require(trigger>now&&trigger<=now+86400000L){"Alarm is expired or beyond 24 hours"}
        val target=Instant.ofEpochMilli(trigger).atZone(zone)
        val localNow=Instant.ofEpochMilli(now).atZone(zone)
        var date=localNow.toLocalDate()
        if(!target.toLocalTime().isAfter(localNow.toLocalTime()))date=date.plusDays(1)
        val wall=date.atTime(target.hour,target.minute)
        val offsets=zone.rules.getValidOffsets(wall)
        require(offsets.size==1){"Ambiguous or nonexistent local time at DST transition"}
        require(wall.toInstant(offsets[0]).toEpochMilli()==trigger){"Clock cannot represent this date; choose the next local occurrence"}
        return target.hour to target.minute
    }
    fun cancel(launchedOrUncertain: Boolean)=if(launchedOrUncertain)"user_action_required" else "cancelled"
}
