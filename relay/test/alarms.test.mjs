// Copyright 2026 Rafail Andreou. SPDX-License-Identifier: GPL-2.0-only
import test from 'node:test';
import assert from 'node:assert/strict';
import {addAlarm,mutateAlarm,alarmStatus,validateAlarmReports,alarmDefinition} from '../src/alarms.mjs';
import {emptyModes,mutateModes} from '../src/device-modes.mjs';
import {tools} from '../src/worker.mjs';
const now=Date.parse('2026-10-31T09:00:00Z');
const request={triggerAt:'2026-10-31T13:00:00+02:00',label:'Wake up',requestId:'test'};
test('absolute alarm timestamps persist as UTC and migrate old state additively',()=>{
 const s=emptyModes();const old=JSON.stringify(s.templates);const a=addAlarm(s,request,now);
 assert.equal(a.triggerAtMs,Date.parse('2026-10-31T11:00:00Z'));assert.equal(JSON.stringify(s.templates),old);assert.equal(JSON.parse(JSON.stringify(s)).alarms[0].id,a.id);
});
test('stable request ids suppress duplicates and reject conflicting retries',()=>{
 const s=emptyModes();addAlarm(s,request,now);addAlarm(s,request,now+1000);assert.equal(s.alarms.length,1);
 assert.throws(()=>addAlarm(s,{...request,label:'Different'},now));
});
test('invalid, expired, ambiguous or imprecise timestamps and recurrence rejected',()=>{
 for(const triggerAt of ['2026-10-31T13:00:00','2026-02-31T13:00:00Z','2026-10-31T08:00:00Z','2026-11-05T13:00:00Z','2026-10-31T13:00:01Z'])assert.throws(()=>addAlarm(emptyModes(),{...request,triggerAt},now));
 assert.throws(()=>addAlarm(emptyModes(),{...request,daysOfWeek:[1]},now));assert.throws(()=>alarmDefinition({enabled:true,time:'25:00'}));
});
test('Sleep mode creates a separately identified linked request',()=>{
 const s=emptyModes(),apps=[{packageName:'com.example.chat'}];const t=mutateModes(s,'create_device_mode',{name:'Sleep',mode:'allow_list',allowedPackages:['com.example.chat'],durationMinutes:120,alarm:{enabled:true,label:'Wake up'}},apps,now).item;
 const i=mutateModes(s,'activate_device_mode',{id:t.id},apps,now).item;
 assert.equal(s.alarms[0].associatedActivationId,i.id);assert.equal(s.alarms[0].associatedModeId,t.id);assert.equal(s.alarms[0].triggerAtMs,i.expiresAtMs);
});
test('offline desired state never reports local or system confirmation',()=>{
 const s=emptyModes(),a=addAlarm(s,request,now),phone={deviceModes:{alarms:[{id:a.id,state:'launched',updatedAt:now}]}};
 assert.equal(alarmStatus(s,phone,false,true,now).alarms[0].localAcknowledged,false);
 assert.equal(alarmStatus(s,phone,true,false,now).alarms[0].state,'requested');
 const v=alarmStatus(s,phone,true,true,now).alarms[0];assert.equal(v.state,'launched');assert.equal(v.systemConfirmed,false);
});
test('cancellation waits for a fresh matching phone observation',()=>{
 const s=emptyModes(),a=addAlarm(s,request,now);mutateAlarm(s,'cancel_alarm',{id:a.id},now+1000);
 const phone={deviceModes:{alarms:[{id:a.id,state:'launched',updatedAt:now}]}};
 assert.equal(alarmStatus(s,phone,true,true,now).alarms[0].state,'requested');
 phone.deviceModes.alarms[0]={id:a.id,state:'user_action_required',updatedAt:now+2000};
 assert.equal(alarmStatus(s,phone,true,true,now).alarms[0].userActionRequired,true);
 assert.throws(()=>mutateAlarm(s,'cancel_alarm',{id:'unknown'},now));
});
test('malformed observations rejected and tools preserve OAuth',()=>{
 assert.throws(()=>validateAlarmReports([{id:'a',state:'ringing',updatedAt:now}]));
 for(const name of ['set_alarm','list_remote_alarms','cancel_alarm']){const t=tools.find(t=>t.name===name);assert.equal(t.securitySchemes[0].type,'oauth2');}
});
