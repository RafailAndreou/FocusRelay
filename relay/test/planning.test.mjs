// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
import test from 'node:test';
import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import {readFileSync,readdirSync} from 'node:fs';
import worker,{sign,tools} from '../src/worker.mjs';
import {timing,mutatePlanning,pruneAllowlist,LIMITS} from '../src/planning.mjs';
import {windows,evaluateRule,zonedInstant} from '../src/schedule-time.mjs';
const app={packageName:'com.instagram.android',label:'Instagram'};
const second={packageName:'com.example.cola',label:'Coca Cola'};
const origin='https://mindful.example.workers.dev';
const t=Date.parse('2026-10-20T12:00:00Z');
const blank=()=>({schemaVersion:2,schedules:[],presets:[]});
const once=(start=t+3600000,end=t+7200000)=>({type:'once',startAt:new Date(start).toISOString(),endAt:new Date(end).toISOString()});
const weekly=(extra={})=>({type:'weekly',timeZone:'Asia/Nicosia',daysOfWeek:[1,2,3,4,5],startTime:'20:00',endTime:'22:00',...extra});
function create(s,timingArg=once(),packages=[app.packageName]) {return mutatePlanning(s,'create_schedule',{name:'Evening',packages,timing:timingArg},[app,second],t).item;}
class D1 {
 constructor(){this.db=new DatabaseSync(':memory:');for(const f of readdirSync(new URL('../migrations/',import.meta.url)).filter(f=>f.endsWith('.sql')).sort())this.db.exec(readFileSync(new URL('../migrations/'+f,import.meta.url),'utf8'));}
 prepare(sql){const stmt=this.db.prepare(sql);let vals=[];const q={bind(...v){vals=v;return q;},async first(){return stmt.get(...vals)||null;},async run(){const r=stmt.run(...vals);return {meta:{changes:Number(r.changes)}};}};return q;}
 async batch(s){return Promise.all(s.map(x=>x.run()));}
}
function env(){return {DB:new D1(),OWNER_SECRET:'owner_'+ 'a'.repeat(40),DEVICE_TOKEN:'device_'+ 'b'.repeat(40),AUTH_SECRET:'auth_'+ 'c'.repeat(40)};}
async function api(e,path,body,token=e.OWNER_SECRET){return worker.fetch(new Request(origin+path,{method:'POST',headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'},body:JSON.stringify(body)}),e);}
function phone(extra={}) {return {schemaVersion:2,enabled:true,usageAccess:true,overlay:true,trackerAvailable:true,serviceAvailable:true,schedulerReady:true,schedulerMode:'exact',nextTransitionMs:null,lastSuccessfulSyncMs:Date.now(),clockSkewMs:0,timeZone:'Asia/Nicosia',activeBlocks:[],scheduleErrors:[],...extra};}
async function sync(e,revision=-1,apps=[app],extra={}){return api(e,'/device/sync',{apps,appliedRevision:revision,status:phone(extra)},e.DEVICE_TOKEN);}
async function status(e){return (await worker.fetch(new Request(origin+'/api/status',{headers:{Authorization:'Bearer '+e.OWNER_SECRET}}),e)).json();}
async function op(e,name,args){return api(e,'/api/remote/'+name,args);}

test('future one-time schedule uses absolute UTC instants and remains scheduled before start',()=>{
 const s=blank(),r=create(s,{type:'once',startAt:'2026-10-21T09:00:00+03:00',endAt:'2026-10-21T11:00:00+03:00'});
 assert.equal(r.timing.startAtMs,Date.parse('2026-10-21T06:00:00Z'));
 assert.equal(evaluateRule(r,[],t).phase,'scheduled');assert.equal(evaluateRule(r,[],r.timing.startAtMs).phase,'active');assert.equal(evaluateRule(r,[],r.timing.endAtMs).phase,'expired');
});
test('weekly recurrence uses ISO weekdays and named zone rather than phone timezone',()=>{
 const w=timing(weekly(),t);const occurrences=windows(w,Date.parse('2026-10-19T00:00:00Z'),Date.parse('2026-10-26T00:00:00Z'));
 assert.equal(occurrences.length,5);assert.equal(new Date(occurrences[0].startAtMs).toISOString(),'2026-10-19T17:00:00.000Z');
});
test('overnight window belongs to its start weekday, including next morning',()=>{
 const r=create(blank(),weekly({daysOfWeek:[1],startTime:'23:30',endTime:'07:30'}));
 assert.equal(evaluateRule(r,[],Date.parse('2026-10-20T03:00:00Z')).phase,'active');
 assert.notEqual(evaluateRule(r,[],Date.parse('2026-10-21T03:00:00Z')).phase,'active');
});
test('DST spring gap shifts forward; autumn overlap chooses earlier start and later end',()=>{
 assert.equal(new Date(zonedInstant('2026-03-29','03:30','Asia/Nicosia')).toISOString(),'2026-03-29T01:30:00.000Z');
 assert.equal(new Date(zonedInstant('2026-10-25','03:15','Asia/Nicosia')).toISOString(),'2026-10-25T00:15:00.000Z');
 assert.equal(new Date(zonedInstant('2026-10-25','03:45','Asia/Nicosia',true)).toISOString(),'2026-10-25T01:45:00.000Z');
 const w=timing(weekly({daysOfWeek:[7],startTime:'23:30',endTime:'07:30'}),t);
 assert.equal(windows(w,Date.parse('2026-03-22T00:00:00Z'),Date.parse('2026-03-24T00:00:00Z'))[0].endAtMs-windows(w,Date.parse('2026-03-22T00:00:00Z'),Date.parse('2026-03-24T00:00:00Z'))[0].startAtMs,8*3600000);
});
test('overlapping rules survive independent deletion and pause/resume never restarts windows',()=>{
 const s=blank(),a=create(s),b=create(s,once(t+3600000,t+10800000));
 mutatePlanning(s,'pause_remote',{targetType:'schedule',id:a.id,until:new Date(t+4500000).toISOString()},[app],t);
 assert.equal(evaluateRule(a,[],t+4000000).phase,'paused');assert.equal(evaluateRule(b,[],t+4000000).phase,'active');
 assert.equal(evaluateRule(a,[],t+5000000).phase,'active');
 mutatePlanning(s,'resume_remote',{targetType:'schedule',id:a.id},[app],t);assert.equal(a.timing.endAtMs,t+7200000);
 mutatePlanning(s,'delete_schedule',{id:a.id},[app],t);assert.equal(evaluateRule(a,[],t+4000000).phase,'deleted');assert.equal(evaluateRule(b,[],t+4000000).phase,'active');
});
test('presets are inactive templates; activations snapshot defaults and parent pause/delete applies',()=>{
 const s=blank();const p=mutatePlanning(s,'create_preset',{name:'Study',packages:[app.packageName],durationMinutes:90},[app],t).item;
 assert.equal(s.schedules.length,0);const r=mutatePlanning(s,'activate_preset',{id:p.id},[app],t).item;
 assert.equal(r.timing.endAtMs,t+90*60000);
 mutatePlanning(s,'pause_remote',{targetType:'preset',id:p.id},[app],t);assert.equal(evaluateRule(r,s.presets,t).phase,'paused');
 mutatePlanning(s,'resume_remote',{targetType:'preset',id:p.id},[app],t);assert.equal(evaluateRule(r,s.presets,t).phase,'active');
 mutatePlanning(s,'delete_preset',{id:p.id},[app],t);assert.equal(evaluateRule(r,s.presets,t).phase,'deleted');
});
test('invalid packages, timezone/date definitions and oversized collections fail',()=>{
 for(const value of [weekly({timeZone:'Mars/Base'}),weekly({daysOfWeek:[0]}),weekly({daysOfWeek:[1,1]}),weekly({startTime:'24:00'}),weekly({endTime:'20:00'}),weekly({startDate:'2026-02-30'}),{type:'once',startAt:'2026-10-21T09:00:00',endAt:'2026-10-21T10:00:00Z'}])assert.throws(()=>timing(value,t));
 assert.throws(()=>create(blank(),once(),['com.bad;DROP']));assert.throws(()=>create(blank(),once(),['com.unknown']));
 const s=blank();for(let i=0;i<LIMITS.schedules;i++)create(s);assert.throws(()=>create(s));
});
test('allowlist revocation prunes every reference and disables empty rules without widening opt-in',()=>{
 const s=blank();const r=create(s,once(),[app.packageName,second.packageName]);
 let p=pruneAllowlist(s,[{packageName:app.packageName,untilMs:t+10000}],[second],t);assert.equal(p.changed,true);assert.deepEqual(r.packages,[second.packageName]);assert.deepEqual(p.blocks,[]);
 pruneAllowlist(s,[],[],t);assert.equal(r.disabled,true);assert.equal(evaluateRule(r,[],t).phase,'unavailable');
});
test('migration preserves immediate blocks and OAuth tables',()=>{
 const db=new DatabaseSync(':memory:');db.exec(readFileSync(new URL('../migrations/0001_initial.sql',import.meta.url),'utf8'));db.exec("UPDATE device SET blocks='[{\"packageName\":\"com.example.a\",\"untilMs\":123}]',revision=7");db.exec(readFileSync(new URL('../migrations/0002_remote_planning.sql',import.meta.url),'utf8'));assert.equal(db.prepare('SELECT * FROM device').get().revision,7);assert.ok(db.prepare('SELECT * FROM device').get().blocks.includes('com.example.a'));assert.equal(db.prepare('SELECT count(*) AS n FROM oauth_clients').get().n,0);
});
test('schedule revision is delivered in advance and only v2 full persistence is acknowledged',async()=>{
 const e=env();await sync(e);const time=Date.now();const r=await op(e,'create_schedule',{name:'Tomorrow',packages:[app.packageName],timing:once(time+3600000,time+7200000)});assert.equal(r.status,202);const {revision}=await r.json();
 const desired=await (await sync(e)).json();assert.equal(desired.schemaVersion,2);assert.equal(desired.schedules.length,1);assert.equal((await status(e)).acknowledged,false);
 await sync(e,revision,[app],{schedulerReady:false});assert.equal((await status(e)).acknowledged,false);
 await sync(e,revision);assert.equal((await status(e)).acknowledged,true);assert.deepEqual((await status(e)).activeRemoteBlocks,[]);
 const legacy={apps:[app],appliedRevision:revision,status:{enabled:true,usageAccess:true,overlay:true}};await api(e,'/device/sync',legacy,e.DEVICE_TOKEN);assert.equal((await status(e)).acknowledged,false);
});
test('status requires fresh acknowledged observation, permissions, tracker and desired rule',async()=>{
 const e=env();await sync(e);const untilMs=Date.now()+60000;const accepted=await (await api(e,'/api/blocks',{packages:[app.packageName],until:new Date(untilMs).toISOString()})).json();
 const activeBlocks=[{packageName:app.packageName,untilMs}];await sync(e,accepted.revision,[app],{activeBlocks});assert.equal((await status(e)).activeRemoteBlocks.length,1);
 await sync(e,accepted.revision,[app],{activeBlocks,enforcementErrors:['Blocking overlay failed: SecurityException']});assert.deepEqual((await status(e)).activeRemoteBlocks,[]);assert.equal((await status(e)).enforcementErrors.length,1);
 await sync(e,accepted.revision,[app],{activeBlocks,trackerAvailable:false});assert.deepEqual((await status(e)).activeRemoteBlocks,[]);
 await sync(e,accepted.revision,[app],{activeBlocks,overlay:false});assert.equal((await status(e)).acknowledged,false);
 await sync(e,accepted.revision,[app],{activeBlocks});e.DB.db.exec('UPDATE device SET last_seen=1');assert.deepEqual((await status(e)).activeRemoteBlocks,[]);
});
test('revoked app increments desired revision and removed app is not redelivered',async()=>{
 const e=env();await sync(e);const r=await (await op(e,'create_schedule',{name:'Future',packages:[app.packageName],timing:once(Date.now()+60000,Date.now()+120000)})).json();
 const next=await (await sync(e,r.revision,[])).json();assert.ok(next.revision>r.revision);assert.deepEqual(next.schedules[0].packages,[]);assert.equal(next.schedules[0].disabled,true);
});
test('phone credential can only pause/resume through narrow override endpoint; OAuth guards new MCP tools',async()=>{
 const e=env();await sync(e);const r=await (await op(e,'create_schedule',{name:'Future',packages:[app.packageName],timing:once(Date.now()+60000,Date.now()+120000)})).json();
 assert.equal((await api(e,'/api/remote/delete_schedule',{id:r.item.id},e.DEVICE_TOKEN)).status,401);
 assert.equal((await api(e,'/api/remote/create_schedule',{},e.DEVICE_TOKEN)).status,401);
 assert.equal((await api(e,'/device/override',{operation:'delete_schedule',targetType:'schedule',id:r.item.id},e.DEVICE_TOKEN)).status,400);
 const newRule=await (await op(e,'create_schedule',{name:'Future2',packages:[app.packageName],timing:once(Date.now()+60000,Date.now()+120000)})).json();
 assert.equal((await api(e,'/device/override',{operation:'pause_remote',targetType:'schedule',id:newRule.item.id},e.DEVICE_TOKEN)).status,200);
 const token=await sign({kind:'access',aud:origin,scope:'mindful:control',exp:Date.now()+60000},e.AUTH_SECRET);
 const mcp=await api(e,'/mcp',{jsonrpc:'2.0',id:1,method:'tools/call',params:{name:'list_schedules',arguments:{}}},token);assert.equal((await mcp.json()).result.isError,undefined);
 assert.equal((await api(e,'/mcp',{jsonrpc:'2.0',id:1,method:'tools/list'},e.OWNER_SECRET)).status,401);
});

test('concurrent immediate and scheduled mutations retain both desired-state components',async()=>{
 const e=env();await sync(e);const time=Date.now();
 const [a,b]=await Promise.all([api(e,'/api/blocks',{packages:[app.packageName],until:new Date(time+60000).toISOString()}),op(e,'create_schedule',{name:'Later',packages:[app.packageName],timing:once(time+120000,time+180000)})]);
 assert.equal(a.status,202);assert.equal(b.status,202);const state=await (await sync(e)).json();assert.equal(state.blocks.length,1);assert.equal(state.schedules.length,1);assert.equal(state.revision,2);
});
test('expired paused schedule reports expired and invalid timestamps retain a readable error',async()=>{
 const state=blank(),r=create(state);mutatePlanning(state,'pause_remote',{targetType:'schedule',id:r.id},[app],t);
 assert.equal(evaluateRule(r,[],t+3*3600000).phase,'expired');
 const e=env();await sync(e);const bad=await api(e,'/api/blocks',{packages:[app.packageName],until:'tomorrow'});assert.equal(bad.status,400);assert.match((await bad.json()).error,/timestamp/);
});

test('MCP discovery exposes typed object schemas without union conversion requirements',()=>{
 const walk=(schema,path)=>{
  assert.ok(['object','array','string','integer','number','boolean'].includes(schema.type),path+' must declare its type');
  for(const key of ['oneOf','anyOf','allOf','const'])assert.equal(key in schema,false,path+' uses '+key);
  if(schema.type==='object'){
   assert.equal(schema.additionalProperties,false,path);
   for(const key of schema.required||[])assert.ok(key in schema.properties,path+'.'+key);
   for(const [key,child] of Object.entries(schema.properties))walk(child,path+'.'+key);
  }
  if(schema.type==='array')walk(schema.items,path+'[]');
 };
 assert.equal(tools.length,34);
 for(const tool of tools)walk(tool.inputSchema,tool.name);
 const schema=tools.find(t=>t.name==='create_schedule').inputSchema.properties.timing;
 assert.deepEqual(schema.properties.type.enum,['once','weekly']);
 assert.ok(schema.properties.startAt&&schema.properties.endAt&&schema.properties.timeZone);
 assert.throws(()=>timing({...once(),timeZone:'Asia/Nicosia'},t));
 assert.throws(()=>timing({type:'weekly',timeZone:'Asia/Nicosia'},t));
});

