// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
// SPDX-License-Identifier: GPL-2.0-only
import {DAY, windows, evaluateRule, paused} from './schedule-time.mjs';
export const LIMITS={schedules:64,presets:16,appsPerRule:50,tombstones:64,snapshotBytes:112*1024};
export class PlanningError extends Error { constructor(status,message){super(message);this.status=status;} }
const reject = message => {throw new PlanningError(400,message);};
const object = v => v && typeof v==='object' && !Array.isArray(v);
export function exactKeys(value, keys) { if (!object(value) || Object.keys(value).some(k=>!keys.includes(k))) reject('Unexpected or malformed parameters'); }
export function instant(value, name) {
  if (typeof value!=='string' || !/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d{1,3})?(?:Z|[+-]\d\d:\d\d)$/.test(value)) reject(name+' requires an ISO timestamp with an explicit UTC offset');
  const ms=Date.parse(value); if (!Number.isSafeInteger(ms) || ms<0) reject('Invalid '+name);
  // Reject normalized impossible dates (e.g. February 30), independently of offset.
  const date=value.slice(0,10); if (new Date(date+'T00:00:00Z').toISOString().slice(0,10)!==date) reject('Invalid calendar date');
  return ms;
}
function date(value) { if (typeof value!=='string' || !/^\d{4}-\d\d-\d\d$/.test(value) || !Number.isFinite(Date.parse(value+'T00:00:00Z')) || new Date(value+'T00:00:00Z').toISOString().slice(0,10)!==value) reject('Invalid calendar date'); return value; }
function title(value) { if (typeof value!=='string' || !value.trim() || value.length>80 || /[\x00-\x1f]/.test(value)) reject('Name must contain 1–80 printable characters'); return value.trim(); }
export function packages(value, apps) {
  if (!Array.isArray(value) || value.length<1 || value.length>LIMITS.appsPerRule || new Set(value).size!==value.length) reject('Choose 1–50 distinct allowed apps');
  const allowed=new Set(apps.map(a=>a.packageName));
  if (value.some(p=>typeof p!=='string' || p.length>255 || !/^[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+$/.test(p) || !allowed.has(p))) reject('An app is not on the remote allowlist; call list_apps first');
  return [...value];
}
export function timing(value, time=Date.now()) {
  if (!object(value)) reject('Missing structured timing');
  if (value.type==='once') {
    exactKeys(value,['type','startAt','endAt']);
    const startAtMs=instant(value.startAt,'startAt'),endAtMs=instant(value.endAt,'endAt');
    if (startAtMs<time-300000 || startAtMs>time+366*DAY || endAtMs<=Math.max(startAtMs,time) || endAtMs-startAtMs>7*DAY) reject('One-time window must start within a year, end in the future, and last at most seven days');
    return {type:'once',startAtMs,endAtMs};
  }
  if (value.type!=='weekly') reject('Timing type must be once or weekly');
  exactKeys(value,['type','timeZone','daysOfWeek','startTime','endTime','startDate','endDate']);
  if (typeof value.timeZone!=='string' || value.timeZone.length>80 || !/^(?:UTC|[A-Za-z_+-]+(?:\/[A-Za-z0-9_+.-]+)+)$/.test(value.timeZone)) reject('Use an IANA time zone such as Asia/Nicosia');
  try {new Intl.DateTimeFormat('en',{timeZone:value.timeZone});} catch {reject('Unknown time zone');}
  if (!Array.isArray(value.daysOfWeek) || value.daysOfWeek.length<1 || value.daysOfWeek.length>7 || new Set(value.daysOfWeek).size!==value.daysOfWeek.length || value.daysOfWeek.some(d=>!Number.isInteger(d)||d<1||d>7)) reject('daysOfWeek must contain distinct ISO weekdays 1 (Monday) to 7 (Sunday)');
  for (const k of ['startTime','endTime']) if (typeof value[k]!=='string' || !/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(value[k])) reject(k+' must be HH:mm');
  if (value.startTime===value.endTime) reject('Equal start/end times are ambiguous; choose a shorter daily window');
  const result={type:'weekly',timeZone:value.timeZone,daysOfWeek:[...value.daysOfWeek].sort(),startTime:value.startTime,endTime:value.endTime};
  for (const k of ['startDate','endDate']) if (value[k]!==undefined) result[k]=date(value[k]);
  if (result.startDate && Date.parse(result.startDate+'T00:00:00Z')>time+366*DAY || result.endDate && result.startDate && result.endDate<result.startDate) reject('Invalid recurrence date range');
  return result;
}
export function readPlanning(row) {return JSON.parse(row.remote_state);}
const newId = () => crypto.randomUUID();
function find(state,type,id) { if (typeof id!=='string' || id.length>80) reject('Invalid id');const item=state[type==='schedule'?'schedules':'presets'].find(x=>x.id===id);if (!item) throw new PlanningError(404,'Unknown '+type);return item; }
function live(item) {if(item.deletedAtMs) throw new PlanningError(409,'This item was deleted');}
function prune(state) {for(const key of ['schedules','presets']) {const dead=state[key].filter(i=>i.deletedAtMs).sort((a,b)=>b.deletedAtMs-a.deletedAtMs).slice(0,LIMITS.tombstones);state[key]=[...state[key].filter(i=>!i.deletedAtMs),...dead];}const ids=new Set(state.presets.map(p=>p.id));state.schedules=state.schedules.filter(r=>!r.presetId||ids.has(r.presetId));}
export function mutatePlanning(state,name,args,apps,time=Date.now()) {
  let item;const schedule=name.includes('schedule');
  if (name==='create_schedule'||name==='update_schedule') {
    exactKeys(args,name==='create_schedule'?['name','packages','timing']:['id','name','packages','timing']);
    const replacement={name:title(args.name),packages:packages(args.packages,apps),timing:timing(args.timing,time)};
    if (name==='create_schedule') {item={id:newId(),...replacement,pausedUntilMs:0,createdAtMs:time,updatedAtMs:time};state.schedules.push(item);}
    else {item=find(state,'schedule',args.id);live(item);Object.assign(item,replacement,{disabled:false,disabledReason:null,updatedAtMs:time});}
  } else if (name==='create_preset'||name==='update_preset') {
    exactKeys(args,name==='create_preset'?['name','packages','durationMinutes','timing']:['id','name','packages','durationMinutes','timing']);
    if (args.durationMinutes!==undefined && (!Number.isInteger(args.durationMinutes)||args.durationMinutes<1||args.durationMinutes>10080)) reject('durationMinutes must be 1–10080');
    const replacement={name:title(args.name),packages:packages(args.packages,apps),durationMinutes:args.durationMinutes??null,timing:args.timing?timing(args.timing,time):null};
    if (name==='create_preset') {item={id:newId(),...replacement,pausedUntilMs:0,createdAtMs:time,updatedAtMs:time};state.presets.push(item);}
    else {item=find(state,'preset',args.id);live(item);Object.assign(item,replacement,{disabled:false,disabledReason:null,updatedAtMs:time});}
  } else if (name==='activate_preset') {
    exactKeys(args,['id','durationMinutes']);const preset=find(state,'preset',args.id);live(preset);
    if (paused(preset,time)||preset.disabled) reject('Resume or repair this preset before activating it');
    const allowed=packages(preset.packages,apps);const minutes=args.durationMinutes??preset.durationMinutes;
    let t;
    if (minutes!==undefined && minutes!==null) {if (!Number.isInteger(minutes)||minutes<1||minutes>10080) reject('durationMinutes must be 1–10080');t={type:'once',startAtMs:time,endAtMs:time+minutes*60000};}
    else if (preset.timing) {t=preset.timing;if(t.type==='once'&&t.endAtMs<=time) reject('Preset window expired; provide a duration or update its timing');}
    else reject('Provide a duration or configure preset timing');
    item={id:newId(),presetId:preset.id,name:preset.name,packages:allowed,timing:t,pausedUntilMs:0,createdAtMs:time,updatedAtMs:time};state.schedules.push(item);
  } else if (name==='delete_schedule'||name==='delete_preset') {
    exactKeys(args,['id']);item=find(state,schedule?'schedule':'preset',args.id);
    item.deletedAtMs=item.deletedAtMs||time;item.updatedAtMs=time;
    if (!schedule) for(const rule of state.schedules.filter(r=>r.presetId===item.id)) {rule.deletedAtMs=rule.deletedAtMs||time;rule.updatedAtMs=time;}
  } else if (name==='pause_remote'||name==='resume_remote') {
    exactKeys(args,name==='pause_remote'?['targetType','id','until']:['targetType','id']);
    if (!['schedule','preset'].includes(args.targetType)) reject('targetType must be schedule or preset');
    item=find(state,args.targetType,args.id);live(item);
    let until=0;
    if (name==='pause_remote') {until=args.until===undefined?-1:instant(args.until,'until');if(until!==-1&&(until<=time||until>time+7*DAY)) reject('Pause end must be within seven days');}
    item.pausedUntilMs=until;item.updatedAtMs=time;
  } else throw new PlanningError(400,'Unknown planning operation');
  prune(state);
  if (state.schedules.filter(r=>!r.deletedAtMs).length>LIMITS.schedules || state.presets.filter(p=>!p.deletedAtMs).length>LIMITS.presets) reject('Schedule or preset limit reached; delete unused items');
  if (JSON.stringify(state).length>LIMITS.snapshotBytes) reject('Desired snapshot is too large');
  return {item};
}
export function pruneAllowlist(state,blocks,apps,time=Date.now()) {
  const allowed=new Set(apps.map(a=>a.packageName));let changed=false;
  const filtered=blocks.filter(b=>allowed.has(b.packageName));if(filtered.length!==blocks.length)changed=true;
  for(const item of [...state.schedules,...state.presets]) {
    const keep=item.packages.filter(p=>allowed.has(p));
    if(keep.length!==item.packages.length){item.packages=keep;item.updatedAtMs=time;changed=true;if(!keep.length){item.disabled=true;item.disabledReason='app_not_allowed';}}
  }
  return {changed,blocks:filtered};
}
export function describePlanning(state,time,acknowledged=false,ready=false) {
  const schedules=state.schedules.map(rule=>{
    const e=evaluateRule(rule,state.presets,time);
    return {...rule,desiredPhase:e.phase,state:e.phase==='active'&&!(acknowledged&&ready)?'pending':e.phase,nextWindow:e.windows.find(w=>w.endAtMs>time)||null};
  });
  return {schedules,presets:state.presets.map(p=>({...p,state:p.deletedAtMs?'deleted':p.disabled?'unavailable':paused(p,time)?'paused':schedules.some(s=>s.presetId===p.id&&s.state==='active')?'active':'ready'}))};
}
export function listPlanning(state,args,time=Date.now()) {
  exactKeys(args,['from','to','includeDeleted']);
  if(args.includeDeleted!==undefined&&typeof args.includeDeleted!=='boolean') reject('includeDeleted must be boolean');
  const from=args.from?instant(args.from,'from'):time,to=args.to?instant(args.to,'to'):from+DAY;
  if(to<=from||to-from>31*DAY||from>time+366*DAY) reject('Query at most 31 days within the next year');
  const result=describePlanning(state,time);
  return {schedules:result.schedules.filter(r=>args.includeDeleted||!r.deletedAtMs).map(r=>({...r,occurrences:r.deletedAtMs||r.disabled?[]:windows(r.timing,from,to).slice(0,64)})),from:new Date(from).toISOString(),to:new Date(to).toISOString()};
}

