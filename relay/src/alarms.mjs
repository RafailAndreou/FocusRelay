// Copyright 2026 Rafail Andreou. SPDX-License-Identifier: GPL-2.0-only
import {PlanningError,exactKeys,instant} from './planning.mjs';
const bad=m=>{throw new PlanningError(400,m);};
const obj=(properties,required=[])=>({type:'object',properties,required,additionalProperties:false});
export const alarmSchema=obj({enabled:{type:'boolean'},triggerAt:{type:'string',format:'date-time'},time:{type:'string',pattern:'^(?:[01][0-9]|2[0-3]):[0-5][0-9]$'},label:{type:'string',maxLength:80},skipUi:{type:'boolean'}},['enabled']);
export function alarmDefinition(a){
 exactKeys(a,['enabled','triggerAt','time','label','skipUi']);
 if(typeof a.enabled!=='boolean')bad('alarm.enabled must be boolean');
 if(!a.enabled)return {enabled:false};
 if(a.triggerAt!==undefined&&a.time!==undefined)bad('Choose alarm.triggerAt or time');
 if(a.triggerAt!==undefined)instant(a.triggerAt,'alarm.triggerAt');
 if(a.time!==undefined&&!/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(a.time))bad('Invalid alarm time');
 if(a.label!==undefined&&(typeof a.label!=='string'||!a.label.trim()||a.label.length>80||/[\x00-\x1f]/.test(a.label)))bad('Invalid alarm label');
 if(a.skipUi!==undefined&&typeof a.skipUi!=='boolean')bad('Invalid skipUi');
 return {...a,label:a.label||'Wake up',skipUi:a.skipUi===true};
}
export const alarmTools=[
 {name:'set_alarm',description:'Request a system Clock alarm. Supply an absolute triggerAt with explicit offset, to minute precision, within 24 hours. Phone must confirm it is the next occurrence of that wall time. Open FocusRelay to launch Clock; no background launch or guaranteed system creation. Retry with the same requestId. Recurrence is unsupported in V1.',inputSchema:obj({triggerAt:{type:'string',format:'date-time'},label:{type:'string',maxLength:80},skipUi:{type:'boolean'},requestId:{type:'string',pattern:'^[A-Za-z0-9_-]{1,64}$'}},['triggerAt','requestId']),annotations:{readOnlyHint:false,destructiveHint:false,openWorldHint:false}},
 {name:'list_remote_alarms',description:'List only FocusRelay-requested alarm metadata and latest phone observations. Launched is unverified; Android does not expose a creation receipt or arbitrary Clock state.',inputSchema:obj({}),annotations:{readOnlyHint:true,destructiveHint:false,openWorldHint:false}},
 {name:'cancel_alarm',description:'Cancel an unlaunched request by its FocusRelay id. Already handed-off or uncertain requests require managing the alarm in Clock; never claims silent system cancellation. Phone acknowledgement is required.',inputSchema:obj({id:{type:'string',maxLength:80}},['id']),annotations:{readOnlyHint:false,destructiveHint:false,openWorldHint:false}},
];
export const alarmOperations=new Set(['set_alarm','cancel_alarm']);
export function addAlarm(state,args,now=Date.now(),links={}){
 exactKeys(args,['triggerAt','label','skipUi','requestId']);
 if(typeof args.requestId!=='string'||!/^[-A-Za-z0-9_]{1,64}$/.test(args.requestId))bad('Provide a stable requestId');
 state.alarms??=[];
 const id='alarm-'+args.requestId,old=state.alarms.find(x=>x.id===id);
 const definition=alarmDefinition({enabled:true,triggerAt:args.triggerAt,label:args.label,skipUi:args.skipUi});
 const triggerAtMs=instant(args.triggerAt,'triggerAt');
 if(triggerAtMs%60000)bad('Alarm must have minute precision');
 if(old){if(old.triggerAtMs!==triggerAtMs||old.label!==definition.label||old.skipUi!==definition.skipUi)bad('requestId already used with different parameters');return old;}
 if(triggerAtMs<=now||triggerAtMs>now+86400000)bad('Alarm must be in the next 24 hours');
 state.alarms=state.alarms.filter(x=>x.triggerAtMs>now-7*86400000);
 if(state.alarms.length>=32)bad('Alarm metadata limit reached');
 const item={id,triggerAtMs,label:definition.label,skipUi:definition.skipUi,daysOfWeek:[],createdAt:now,updatedAt:now,state:'requested',...links};state.alarms.push(item);return item;
}
export function mutateAlarm(state,name,args,now=Date.now()){
 if(name==='set_alarm')return {item:addAlarm(state,args,now)};
 exactKeys(args,['id']);const item=(state.alarms||[]).find(x=>x.id===args.id);if(!item)throw new PlanningError(404,'Alarm not_found');
 if(!item.cancelRequestedAt){item.cancelRequestedAt=now;item.updatedAt=now;}
 return {item,status:'requested',message:'Cancellation pending phone observation. If already handed to Clock, user action is required.'};
}
export function alarmStatus(state,phone,acknowledged,online,now=Date.now()){
 const reports=new Map((phone.deviceModes?.alarms||[]).map(a=>[a.id,a]));
 const alarms=(state.alarms||[]).map(a=>{
  const observed=reports.get(a.id);const fresh=acknowledged&&online&&observed&&(!a.cancelRequestedAt||observed.updatedAt>=a.cancelRequestedAt);
  return {...a,state:fresh?observed.state:'requested',localAcknowledged:!!fresh,observationFresh:!!fresh,observed:observed||null,systemConfirmed:false,userActionRequired:fresh?observed.state==='user_action_required':false};
 });
 return {alarms,nextRemoteAlarm:alarms.filter(a=>!a.cancelRequestedAt&&a.triggerAtMs>now).sort((a,b)=>a.triggerAtMs-b.triggerAtMs)[0]||null};
}
export function validateAlarmReports(v){
 if(v===undefined)return [];
 if(!Array.isArray(v)||v.length>32)bad('Invalid alarm report');
 const states=['requested','user_action_required','launched','cancelled','failed','expired'];
 return v.map(a=>{if(!a||typeof a.id!=='string'||a.id.length>80||!states.includes(a.state)||!Number.isSafeInteger(a.updatedAt)||a.updatedAt<0)bad('Invalid alarm observation');return {id:a.id,state:a.state,updatedAt:a.updatedAt,error:typeof a.error==='string'?a.error.slice(0,160):null};});
}
