// Copyright 2026 Rafail Andreou. SPDX-License-Identifier: GPL-2.0-only
import {PlanningError, exactKeys, instant, timing} from './planning.mjs';
import {windows, DAY} from './schedule-time.mjs';
export const MODE_LIMITS={templates:32,instances:64,exceptions:64,sequence:16,maxDurationMs:7*DAY,leaseMs:30*DAY};
const bad=message=>{throw new PlanningError(400,message);};
const id=()=>crypto.randomUUID();
const pattern=/^[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+$/;
const text=(v,key,max=80)=>{if(typeof v!=='string'||!v.trim()||v.length>max||/[\x00-\x1f]/.test(v))bad('Invalid '+key);return v.trim();};
const integer=(v,key,min,max)=>{if(!Number.isSafeInteger(v)||v<min||v>max)bad(`${key} must be ${min}–${max}`);return v;};
export const emptyModes=()=>({schemaVersion:1,templates:[],instances:[],exceptions:[]});
export function readModes(row){const s=JSON.parse(row.assistant_state);if(s.schemaVersion!==1)throw new PlanningError(503,'Unsupported mode state');return s;}
function names(v,key,empty=true){if(!Array.isArray(v)||v.length>(empty?500:500)||!empty&&!v.length||new Set(v).size!==v.length||v.some(p=>typeof p!=='string'||p.length>255||!pattern.test(p)))bad('Invalid '+key);return [...v];}
const validKeys=['name','icon','description','mode','packages','allowedPackages','blockedPackages','excludedPackages','durationMinutes','timing','dnd','brightness','mediaVolume','ringtoneVolume','alarmTime','notificationPolicy'];
export function modeDefinition(args,apps,now=Date.now()){
 exactKeys(args,validKeys);
 if(!['block_list','allow_list'].includes(args.mode))bad('mode must be block_list or allow_list');
 const eligible=new Set(apps.map(a=>a.packageName));
 const allowedPackages=names(args.allowedPackages??[],'allowedPackages',args.mode!=='allow_list');
 const blockedPackages=names(args.packages??args.blockedPackages??[],'packages',args.mode!=='block_list');
 if(args.packages!==undefined&&args.blockedPackages!==undefined)bad('Use packages or blockedPackages, not both');
 if(args.mode==='allow_list'&&blockedPackages.length||args.mode==='block_list'&&allowedPackages.length)bad('Do not mix allow-list and block-list fields');
 if(blockedPackages.some(p=>!eligible.has(p)))bad('Blocking requires current local app opt-in');
 const d={name:text(args.name,'name'),icon:args.icon===undefined?'focus':text(args.icon,'icon',24),description:args.description===undefined?'':text(args.description,'description',500),mode:args.mode,allowedPackages,blockedPackages,excludedPackages:names(args.excludedPackages??[],'excludedPackages'),durationMinutes:args.durationMinutes===undefined?null:integer(args.durationMinutes,'durationMinutes',1,10080),timing:args.timing?timing(args.timing,now):null};
 for(const k of ['brightness','mediaVolume','ringtoneVolume'])if(args[k]!==undefined)d[k]=integer(args[k],k,k==='mediaVolume'?0:k==='brightness'?5:1,100);
 if(args.dnd!==undefined){if(typeof args.dnd!=='boolean')bad('dnd must be boolean');d.dnd=args.dnd;}
 if(args.alarmTime!==undefined)d.alarmAtMs=instant(args.alarmTime,'alarmTime');
 if(args.notificationPolicy!==undefined){if(!['unchanged','alarms_only'].includes(args.notificationPolicy))bad('Only unchanged or alarms_only notification policy is supported');d.notificationPolicy=args.notificationPolicy;if(args.notificationPolicy==='alarms_only')d.dnd=true;}
 return d;
}
export function evaluateModes(s,eligible,now=Date.now(),stopped=new Set()){
 const blocks=new Map(),states=[],transitions=[],activeExceptions=[];
 for(const inst of s.instances){
  let phase='expired',current=null;
  if(inst.stoppedAtMs||stopped.has(inst.id))phase='stopped';
  else if(inst.expiresAtMs>now){
   const ws=windows(inst.timing,now-DAY,Math.min(now+8*DAY,inst.expiresAtMs));
   current=ws.find(w=>w.startAtMs<=now&&w.endAtMs>now)||null;
   phase=current?'active':ws.some(w=>w.startAtMs>now)?'scheduled':'expired';
   if(inst.pausedUntilMs===-1||inst.pausedUntilMs>now)phase='paused';
   for(const w of ws)for(const t of [w.startAtMs,w.endAtMs])if(t>now)transitions.push(t);
   if(inst.pausedUntilMs>now)transitions.push(inst.pausedUntilMs);
   transitions.push(inst.expiresAtMs);
  }
  const denied=[];
  if(phase==='active'){
   const allow=new Set(inst.allowedPackages),excluded=new Set(inst.excludedPackages);
   const exceptions=s.exceptions.filter(e=>e.instanceId===inst.id&&!e.revokedAtMs&&e.untilMs>now&&e.createdAtMs<=now);
   const exceptionPackages=new Set(exceptions.map(e=>e.packageName));
   activeExceptions.push(...exceptions);transitions.push(...exceptions.map(e=>e.untilMs));
   const candidates=inst.mode==='allow_list'?[...eligible].filter(p=>!allow.has(p)):inst.blockedPackages.filter(p=>eligible.has(p));
   for(const p of candidates)if(!excluded.has(p)&&!exceptionPackages.has(p)){denied.push(p);blocks.set(p,Math.max(blocks.get(p)||0,Math.min(current.endAtMs,inst.expiresAtMs)));}
  }
  states.push({...inst,phase,activeWindow:current,blockedPackagesNow:denied});
 }
 return {blocks,states,activeExceptions,nextTransitionMs:transitions.filter(t=>t>now).sort((a,b)=>a-b)[0]??null};
}
function find(s,key,value){text(value,'id');const x=s[key].find(x=>x.id===value);if(!x)throw new PlanningError(404,'Unknown mode or instance');return x;}
const overrides=['durationMinutes','until','allowedPackages','blockedPackages','alarmTime','brightness','dnd','mediaVolume','ringtoneVolume'];
function activate(s,t,opts,apps,now){
 exactKeys(opts,overrides);
 const def={...t};delete def.id;delete def.createdAtMs;delete def.updatedAtMs;
 const incoming={...opts};delete incoming.until;
 const base=Object.fromEntries(Object.entries(def).filter(([k])=>validKeys.includes(k)&&k!=='timing'&&def[k]!==null));
 if(!base.description)delete base.description;
 if(opts.dnd!==undefined)delete base.notificationPolicy;
 if(opts.allowedPackages!==undefined&&opts.blockedPackages!==undefined)bad('Choose allowedPackages or blockedPackages');
 if(opts.allowedPackages!==undefined){base.mode='allow_list';base.blockedPackages=[];}
 if(opts.blockedPackages!==undefined){base.mode='block_list';base.allowedPackages=[];}
 const a=modeDefinition({...base,...incoming},apps,now);
 let at;
 if(opts.until!==undefined){if(opts.durationMinutes!==undefined)bad('Choose durationMinutes or until');at={type:'once',startAtMs:now,endAtMs:instant(opts.until,'until')};}
 else if(opts.durationMinutes!==undefined||a.durationMinutes!==null)at={type:'once',startAtMs:now,endAtMs:now+(opts.durationMinutes??a.durationMinutes)*60000};
 else if(t.timing)at=t.timing;else bad('Provide a duration, until or template timing');
 if(at.type==='once'&&(at.endAtMs<=Math.max(now,at.startAtMs)||at.endAtMs-at.startAtMs>MODE_LIMITS.maxDurationMs))bad('Mode must expire within seven days');
 const expiresAtMs=at.type==='once'?at.endAtMs:now+MODE_LIMITS.leaseMs;
 const alarmAtMs=opts.alarmTime!==undefined?instant(opts.alarmTime,'alarmTime'):t.alarmAtMs;
 if(alarmAtMs!==undefined&&(alarmAtMs<=now||alarmAtMs>now+DAY))bad('Alarm handoff supports only the next 24 hours');
 const item={...a,id:id(),templateId:t.id,timing:at,expiresAtMs,createdAtMs:now,pausedUntilMs:0};
 if(alarmAtMs!==undefined)item.alarmAtMs=alarmAtMs;
 s.instances.push(item);return item;
}
export const modeMutations=new Set(['create_device_mode','update_device_mode','delete_device_mode','activate_device_mode','stop_device_mode','pause_device_mode','resume_device_mode','grant_temporary_app_access','revoke_temporary_app_access','create_mode_sequence']);
export function mutateModes(s,name,args,apps,now=Date.now()){
 let item;
 // Bounded tombstones retain activation snapshots for recovery/history, not forever.
 s.instances=s.instances.filter(i=>(i.stoppedAtMs||i.expiresAtMs)>now-MODE_LIMITS.leaseMs);
 const ids=new Set(s.instances.map(i=>i.id));s.exceptions=s.exceptions.filter(e=>ids.has(e.instanceId)&&e.untilMs>now-DAY);
 if(name==='create_device_mode'||name==='update_device_mode'){
  const {id:modeId,...definition}=args;
  if(name==='create_device_mode'&&modeId!==undefined)bad('Do not supply id for creation');
  const d=modeDefinition(definition,apps,now);
  if(name==='create_device_mode'){item={...d,id:id(),createdAtMs:now,updatedAtMs:now};s.templates.push(item);}
  else {const old=find(s,'templates',modeId);item={...d,id:old.id,createdAtMs:old.createdAtMs,updatedAtMs:now};s.templates=s.templates.map(t=>t.id===old.id?item:t);}
 }else if(name==='delete_device_mode'){
  exactKeys(args,['id']);item=find(s,'templates',args.id);s.templates=s.templates.filter(t=>t.id!==item.id);
  // Deleting a template does not mutate its already snapshotted activations.
 }else if(name==='activate_device_mode'){
  exactKeys(args,['id',...overrides]);const {id:modeId,...opts}=args;item=activate(s,find(s,'templates',modeId),opts,apps,now);
 }else if(['stop_device_mode','pause_device_mode','resume_device_mode'].includes(name)){
  exactKeys(args,['instanceId']);item=find(s,'instances',args.instanceId);
  if(name==='stop_device_mode')item.stoppedAtMs=item.stoppedAtMs||now;
  else if(item.stoppedAtMs||item.expiresAtMs<=now)bad('Session already ended');
  else item.pausedUntilMs=name==='pause_device_mode'?-1:0;
 }else if(name==='grant_temporary_app_access'){
  exactKeys(args,['instanceId','packageName','durationMinutes']);const inst=find(s,'instances',args.instanceId);
  if(evaluateModes(s,new Set(apps.map(a=>a.packageName)),now).states.find(i=>i.id===inst.id)?.phase!=='active')bad('Exception requires an active instance');
  const pkg=names([args.packageName],'packageName')[0];if(!apps.some(a=>a.packageName===pkg))bad('App is not locally opted in');
  const minutes=integer(args.durationMinutes,'durationMinutes',1,60);
  item={id:id(),instanceId:inst.id,packageName:pkg,createdAtMs:now,untilMs:Math.min(inst.expiresAtMs,now+minutes*60000)};s.exceptions.push(item);
 }else if(name==='revoke_temporary_app_access'){
  exactKeys(args,['id']);item=find(s,'exceptions',args.id);item.revokedAtMs=now;
 }else if(name==='create_mode_sequence'){
  exactKeys(args,['name','startAt','steps']);text(args.name,'name');const start=instant(args.startAt,'startAt');
  if(start<now-300000||start>now+366*DAY||!Array.isArray(args.steps)||!args.steps.length||args.steps.length>MODE_LIMITS.sequence)bad('Invalid sequence');
  const sequenceId=id();let cursor=start;const steps=[];
  for(const step of args.steps){exactKeys(step,['modeId','durationMinutes']);const t=find(s,'templates',step.modeId);const minutes=integer(step.durationMinutes,'durationMinutes',1,10080);
   const x=activate(s,{...t,durationMinutes:null,timing:{type:'once',startAtMs:cursor,endAtMs:cursor+minutes*60000}},{},apps,now);
   x.sequenceId=sequenceId;x.sequenceName=args.name;steps.push(x);cursor=x.expiresAtMs;
  }
  if(cursor-start>MODE_LIMITS.maxDurationMs)bad('Sequence must last at most seven days');item={id:sequenceId,name:args.name,instances:steps};
 }else bad('Unknown mode operation');
 const live=s.instances.filter(i=>!i.stoppedAtMs&&i.expiresAtMs>now);
 const finished=s.instances.filter(i=>i.stoppedAtMs||i.expiresAtMs<=now).sort((a,b)=>(b.stoppedAtMs||b.expiresAtMs)-(a.stoppedAtMs||a.expiresAtMs));
 s.instances=[...live,...finished.slice(0,Math.max(0,Math.min(8,MODE_LIMITS.instances-live.length)))];
 const retained=new Set(s.instances.map(i=>i.id));s.exceptions=s.exceptions.filter(e=>retained.has(e.instanceId)&&!e.revokedAtMs&&e.untilMs>now);
 if(s.templates.length>MODE_LIMITS.templates||s.instances.length>MODE_LIMITS.instances||s.exceptions.length>MODE_LIMITS.exceptions)bad('Mode state limit reached; remove unused items');
 if(new TextEncoder().encode(JSON.stringify(s)).length>96*1024)bad('Mode state too large');
 return {item};
}
export function reconcileModeOptIn(s,apps){
 const eligible=new Set(apps.map(a=>a.packageName));let changed=false;
 for(const item of [...s.templates,...s.instances]){const next=item.blockedPackages.filter(p=>eligible.has(p));if(next.length!==item.blockedPackages.length){item.blockedPackages=next;changed=true;}}
 return changed;
}
