// Copyright 2026 Rafail Andreou. SPDX-License-Identifier: GPL-2.0-only
import test from 'node:test';
import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import {readFileSync,readdirSync} from 'node:fs';
import worker,{tools} from '../src/worker.mjs';
import {emptyModes,mutateModes,evaluateModes} from '../src/device-modes.mjs';
import {queryEvents,saveEvents,validateEvents,validateUsage,validateModeReport} from '../src/mode-protocol.mjs';
const now=Date.parse('2026-10-30T10:00:00Z');
const apps=[{packageName:'com.example.chat',label:'Chat'},{packageName:'com.example.video',label:'Video'},{packageName:'com.example.mail',label:'Mail'}];
const eligible=new Set(apps.map(x=>x.packageName));
const template=(s,extra={})=>mutateModes(s,'create_device_mode',{name:'Thesis',mode:'allow_list',allowedPackages:[apps[0].packageName],durationMinutes:90,...extra},apps,now).item;
const activate=(s,t,extra={})=>mutateModes(s,'activate_device_mode',{id:t.id,...extra},apps,now).item;
test('allow-list resolves a dynamic eligible complement, not a static stored blocked list',()=>{
 const s=emptyModes(),t=template(s),i=activate(s,t);assert.deepEqual(i.blockedPackages,[]);
 assert.deepEqual([...evaluateModes(s,eligible,now).blocks.keys()].sort(),[apps[1].packageName,apps[2].packageName].sort());
 const changed=new Set([apps[0].packageName,'com.new.optedin']);assert.deepEqual([...evaluateModes(s,changed,now).blocks.keys()],['com.new.optedin']);
});
test('block-list restricts only explicitly opted-in eligible packages; protected apps are absent',()=>{
 const s=emptyModes(),t=template(s,{mode:'block_list',allowedPackages:[],packages:[apps[1].packageName]});activate(s,t);
 assert.equal(evaluateModes(s,new Set([apps[0].packageName]),now).blocks.size,0);
 assert.equal(evaluateModes(s,eligible,now).blocks.size,1);
 assert.throws(()=>template(emptyModes(),{mode:'block_list',allowedPackages:[],packages:['com.android.settings']}));
});
test('activation can override access mode and remains independent of template edits/deletion',()=>{
 const s=emptyModes(),t=template(s),i=activate(s,t,{blockedPackages:[apps[2].packageName],durationMinutes:10});
 assert.equal(i.mode,'block_list');assert.equal(i.expiresAtMs,now+600000);
 mutateModes(s,'delete_device_mode',{id:t.id},apps,now);assert.equal(evaluateModes(s,eligible,now).states[0].phase,'active');
});
test('exceptions expire automatically and never release another overlapping mode',()=>{
 const s=emptyModes(),t=template(s),a=activate(s,t),b=activate(s,t);
 const ex=mutateModes(s,'grant_temporary_app_access',{instanceId:a.id,packageName:apps[1].packageName,durationMinutes:10},apps,now).item;
 assert.equal(evaluateModes(s,eligible,now+1000).blocks.has(apps[1].packageName),true);
 mutateModes(s,'stop_device_mode',{instanceId:b.id},apps,now+1000);
 assert.equal(evaluateModes(s,eligible,now+2000).blocks.has(apps[1].packageName),false);
 assert.equal(evaluateModes(s,eligible,ex.untilMs).blocks.has(apps[1].packageName),true);
 assert.equal(evaluateModes(s,eligible,now+2000).nextTransitionMs,ex.untilMs);
});
test('pause/resume never extends expiry and local tombstones defeat redelivery',()=>{
 const s=emptyModes(),i=activate(s,template(s));const end=i.expiresAtMs;
 mutateModes(s,'pause_device_mode',{instanceId:i.id},apps,now+1);assert.equal(evaluateModes(s,eligible,now+2).blocks.size,0);
 mutateModes(s,'resume_device_mode',{instanceId:i.id},apps,now+3);assert.equal(i.expiresAtMs,end);
 assert.equal(evaluateModes(s,eligible,now+4,new Set([i.id])).blocks.size,0);
 assert.equal(evaluateModes(s,eligible,end).blocks.size,0);
});
test('sequences are contiguous absolute snapshots and can transition offline after reboot',()=>{
 const s=emptyModes(),a=template(s),b=template(s,{name:'Programming',allowedPackages:[apps[2].packageName]});
 const result=mutateModes(s,'create_mode_sequence',{name:'Day',startAt:new Date(now).toISOString(),steps:[{modeId:a.id,durationMinutes:60},{modeId:b.id,durationMinutes:30}]},apps,now).item;
 assert.equal(result.instances[1].timing.startAtMs,result.instances[0].expiresAtMs);
 const persisted=JSON.parse(JSON.stringify(s));assert.equal(evaluateModes(persisted,eligible,now+3600000).states[1].phase,'active');
 assert.equal(evaluateModes(persisted,eligible,now+5400000).blocks.size,0);
});
test('recurring activations reuse DST-aware timing and expire their finite lease',()=>{
 const s=emptyModes(),t=template(s,{durationMinutes:undefined,timing:{type:'weekly',timeZone:'Asia/Nicosia',daysOfWeek:[1,2,3,4,5],startTime:'09:00',endTime:'14:00'}});const i=activate(s,t);
 assert.equal(evaluateModes(s,eligible,now).states[0].phase,'active');
 assert.equal(evaluateModes(s,eligible,i.expiresAtMs).blocks.size,0);
});
test('malformed commands, empty allow-lists, mixed modes and unsafe action values are rejected',()=>{
 for(const extra of [{allowedPackages:[]},{mode:'oops'},{brightness:0},{ringtoneVolume:0},{dnd:'yes'},{allowedPackages:['bad package']},{notificationPolicy:'read_notifications'},{durationMinutes:10081}])assert.throws(()=>template(emptyModes(),extra));
 const s=emptyModes(),t=template(s);
 assert.throws(()=>activate(s,t,{until:new Date(now+10000).toISOString(),durationMinutes:2}));
 assert.throws(()=>activate(s,t,{alarmTime:new Date(now+2*86400000).toISOString()}));
});
class D1{
 constructor(){this.db=new DatabaseSync(':memory:');for(const f of readdirSync(new URL('../migrations/',import.meta.url)).filter(f=>f.endsWith('.sql')).sort())this.db.exec(readFileSync(new URL('../migrations/'+f,import.meta.url),'utf8'));}
 prepare(sql){const stmt=this.db.prepare(sql);let v=[];const q={bind(...x){v=x;return q;},async first(){return stmt.get(...v)||null;},async run(){return {meta:{changes:Number(stmt.run(...v).changes)}};}};return q;}
 async batch(a){return Promise.all(a.map(q=>q.run()));}
}
const origin='https://focusrelay.example.workers.dev';
const env=()=>({DB:new D1(),OWNER_SECRET:'owner_'+ 'a'.repeat(40),DEVICE_TOKEN:'device_'+ 'b'.repeat(40),AUTH_SECRET:'auth_'+ 'c'.repeat(40)});
async function api(e,path,body,token=e.OWNER_SECRET){return worker.fetch(new Request(origin+path,{method:body===undefined?'GET':'POST',headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)}),e);}
const report=(extra={})=>({schemaVersion:3,enabled:true,usageAccess:true,overlay:true,trackerAvailable:true,serviceAvailable:true,schedulerReady:true,schedulerMode:'exact',nextTransitionMs:null,lastSuccessfulSyncMs:Date.now(),clockSkewMs:0,timeZone:'Asia/Nicosia',activeBlocks:[],scheduleErrors:[],enforcementErrors:[],deviceModes:{schemaVersion:1,enabled:true,actionsConsent:false,usageSharing:false,eligiblePackages:apps.map(a=>a.packageName),states:[],actionErrors:[]},...extra});
const sync=(e,appliedRevision=-1,status=report(),extra={})=>api(e,'/device/sync',{apps,appliedRevision,status,...extra},e.DEVICE_TOKEN);
const op=(e,name,args)=>api(e,'/api/remote/'+name,args);
test('relay mode commands require local consent and cannot be used with a bare phone token',async()=>{
 const e=env();let r=await op(e,'create_device_mode',{name:'Work',mode:'allow_list',allowedPackages:[apps[0].packageName]});assert.equal(r.status,409);
 await sync(e);r=await op(e,'create_device_mode',{name:'Work',mode:'allow_list',allowedPackages:[apps[0].packageName],durationMinutes:30});assert.equal(r.status,202);
 assert.equal((await api(e,'/api/remote/list_device_modes',{},e.DEVICE_TOKEN)).status,401);
 const local=await api(e,'/device/modes',{operation:'grant_permission',arguments:{}},e.DEVICE_TOKEN);assert.equal(local.status,400);
});
test('schema 3 desired/applied acknowledgement requires fresh phone observations, permissions and actual restrictions',async()=>{
 const e=env();await sync(e);const t=await (await op(e,'create_device_mode',{name:'Work',mode:'allow_list',allowedPackages:[apps[0].packageName],durationMinutes:30})).json();
 const accepted=await (await op(e,'activate_device_mode',{id:t.item.id})).json();assert.equal(accepted.acknowledged,false);
 const desired=await (await sync(e)).json();assert.equal(desired.schemaVersion,3);assert.equal(desired.deviceModes.instances.length,1);
 let status=await (await api(e,'/api/status')).json();assert.equal(status.currentMode,null);
 const dm={...report().deviceModes,states:[{id:accepted.item.id,phase:'active'}]};
 await sync(e,accepted.revision,report({deviceModes:dm}));status=await (await api(e,'/api/status')).json();assert.equal(status.acknowledged,true);assert.equal(status.currentMode,null);
 const activeBlocks=apps.slice(1).map(a=>({packageName:a.packageName,untilMs:accepted.item.expiresAtMs}));
 await sync(e,accepted.revision,report({deviceModes:dm,activeBlocks}));status=await (await api(e,'/api/status')).json();assert.equal(status.currentMode.id,accepted.item.id);assert.equal(status.activeRestrictions.length,2);
 await sync(e,accepted.revision,report({deviceModes:dm,activeBlocks,overlay:false}));assert.equal((await (await api(e,'/api/status')).json()).currentMode,null);
 e.DB.db.exec('UPDATE device SET last_seen=1');assert.equal((await (await api(e,'/api/status')).json()).currentMode,null);
});
test('old phone receives legacy envelope and cannot acknowledge mode-bearing revisions',async()=>{
 const e=env();await sync(e);await op(e,'create_device_mode',{name:'Work',mode:'allow_list',allowedPackages:[apps[0].packageName]});
 const r=await sync(e,1,{enabled:true,usageAccess:true,overlay:true});assert.equal((await r.json()).deviceModes,undefined);assert.equal((await (await api(e,'/api/status')).json()).acknowledged,false);
});
test('offline local stop tombstone is reconciled before snapshot redelivery',async()=>{
 const e=env();await sync(e);const t=await (await op(e,'create_device_mode',{name:'Work',mode:'allow_list',allowedPackages:[apps[0].packageName],durationMinutes:30})).json();
 const a=await (await op(e,'activate_device_mode',{id:t.item.id})).json();
 const desired=await (await sync(e,-1,report(),{stoppedInstanceIds:[a.item.id]})).json();assert.ok(desired.deviceModes.instances[0].stoppedAtMs);assert.ok(desired.revision>a.revision);
});
test('usage is unavailable without consent and never fabricates absent Android data',async()=>{
 const e=env();await sync(e);let x=await (await op(e,'get_device_usage_summary',{})).json();assert.equal(x.available,false);
 const d={...report().deviceModes,usageSharing:true};await sync(e,-1,report({deviceModes:d,usage:{available:false,reason:'permission_missing',collectedAtMs:Date.now()}}));
 x=await (await op(e,'get_app_usage',{packageName:apps[0].packageName})).json();assert.equal(x.available,false);assert.equal(x.seconds,undefined);
});
test('remote event log deduplicates, paginates and validates bounded queries',async()=>{
 const e=env(),time=Date.now();const events=Array.from({length:4},(_,n)=>({id:'e'+n,atMs:time-n,kind:'mode_activated',details:{instanceId:'session'}}));
 await saveEvents(e,events,'phone');await saveEvents(e,events,'phone');
 const args={from:new Date(time-1000).toISOString(),to:new Date(time+1000).toISOString(),limit:2};const first=await queryEvents(e,args),second=await queryEvents(e,{...args,cursor:first.nextCursor});assert.equal(first.items.length,2);assert.equal(second.items.length,2);assert.equal(new Set([...first.items,...second.items].map(x=>x.id)).size,4);
 await assert.rejects(()=>queryEvents(e,{...args,limit:1000}));
});
test('additive schema migration preserves old stored rules and credentials',()=>{
 const db=new DatabaseSync(':memory:');for(const name of ['0001_initial.sql','0002_remote_planning.sql'])db.exec(readFileSync(new URL('../migrations/'+name,import.meta.url),'utf8'));
 db.exec("UPDATE device SET revision=42,remote_state='{\"schemaVersion\":2,\"schedules\":[],\"presets\":[]}'");db.exec(readFileSync(new URL('../migrations/0003_device_modes.sql',import.meta.url),'utf8'));
 const row=db.prepare('SELECT * FROM device').get();assert.equal(row.revision,42);assert.equal(JSON.parse(row.remote_state).schemaVersion,2);assert.deepEqual(JSON.parse(row.assistant_state),emptyModes());
});
test('MCP schemas are explicit, uniquely named, and keep all legacy tools',()=>{
 assert.equal(new Set(tools.map(t=>t.name)).size,tools.length);for(const n of ['block_apps','create_schedule','activate_preset','create_device_mode','grant_temporary_app_access','get_remote_activity_log'])assert.ok(tools.some(t=>t.name===n));
});
test('updating a template clears omitted actions without altering existing activations',()=>{
 const s=emptyModes(),t=template(s,{dnd:true,brightness:30,mediaVolume:20}),active=activate(s,t);
 const revised=mutateModes(s,'update_device_mode',{id:t.id,name:'Thesis',mode:'allow_list',allowedPackages:[apps[0].packageName],durationMinutes:60},apps,now+1000).item;
 assert.equal(revised.dnd,undefined);assert.equal(revised.brightness,undefined);assert.equal(revised.mediaVolume,undefined);
 assert.equal(active.dnd,true);assert.equal(active.brightness,30);assert.equal(activate(s,revised).dnd,undefined);
});
test('explicit dnd false overrides inherited alarms-only policy',()=>{
 const s=emptyModes(),t=template(s,{notificationPolicy:'alarms_only'}),i=activate(s,t,{dnd:false});assert.equal(i.dnd,false);assert.equal(i.notificationPolicy,undefined);
});
test('malformed consent, unsupported telemetry and invasive event fields fail validation',()=>{
 assert.throws(()=>validateModeReport({...report().deviceModes,enabled:'yes'},apps));
 assert.throws(()=>validateModeReport({...report().deviceModes,eligiblePackages:['com.android.settings']},apps));
 assert.throws(()=>validateEvents([{id:'x',atMs:Date.now(),kind:'mode_activated',details:{messageContent:'private'}}]));
 assert.throws(()=>validateEvents([{id:'x',atMs:Date.now(),kind:'notification_content',details:{}}]));
 assert.throws(()=>validateUsage({available:true,collectedAtMs:now,today:{startAtMs:now,endAtMs:now+60000,apps:[{packageName:apps[0].packageName,seconds:100}],screenTimeSeconds:0},modes:[]},apps));
});
test('phone endpoint cannot change opt-in, permissions or owner authorization',async()=>{
 const e=env();await sync(e);
 for(const operation of ['grant_permission','set_allowed_apps','rotate_owner_secret','create_device_mode']){
  const args=operation==='create_device_mode'?{name:'Bad',mode:'block_list',packages:['com.android.settings']}:{enabled:true};
  assert.equal((await api(e,'/device/modes',{operation,arguments:args},e.DEVICE_TOKEN)).status,400);
 }
});
test('completed sessions and expired exceptions cannot permanently exhaust activation capacity',()=>{
 const s=emptyModes(),t=template(s);let time=now;
 for(let n=0;n<100;n++){
  const i=mutateModes(s,'activate_device_mode',{id:t.id,durationMinutes:1},apps,time).item;
  mutateModes(s,'grant_temporary_app_access',{instanceId:i.id,packageName:apps[1].packageName,durationMinutes:1},apps,time);
  time+=120000;
 }
 assert.ok(s.instances.length<=9);assert.ok(s.exceptions.length<=1);
 assert.ok(mutateModes(s,'activate_device_mode',{id:t.id,durationMinutes:1},apps,time).item);
});
test('history retention hides aged events even before the daily cleanup runs',async()=>{
 const e=env(),time=Date.now(),old=time-30*86400000-1000;
 e.DB.db.prepare('INSERT INTO remote_events VALUES(?,?,?,?,?)').run('old',old,'mode_ended','phone','{}');
 const range={from:new Date(old-1000).toISOString(),to:new Date(old+1000).toISOString()};
 assert.equal((await queryEvents(e,range)).items.length,0);
 await worker.scheduled({},e);assert.equal(e.DB.db.prepare('SELECT count(*) AS n FROM remote_events').get().n,0);
});
