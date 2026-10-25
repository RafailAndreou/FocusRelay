// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
// SPDX-License-Identifier: GPL-2.0-only
// Deliberately small calendar: ISO weekdays, local clock windows, named time zones.
export const DAY = 86400000;
const formatters = new Map();
const instantCache = new Map();
const offsetCache = new Map();
function formatter(zone) {
  if (!formatters.has(zone)) {
    if (formatters.size >= 80) formatters.clear();
    formatters.set(zone, new Intl.DateTimeFormat('en-CA', {timeZone:zone,year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hourCycle:'h23'}));
  }
  return formatters.get(zone);
}
export function localParts(ms, zone) {
  const p = Object.fromEntries(formatter(zone).formatToParts(ms).filter(p=>p.type!=='literal').map(p=>[p.type,Number(p.value)]));
  return {year:p.year,month:p.month,day:p.day,hour:p.hour,minute:p.minute,second:p.second};
}
const wall = p => Date.UTC(p.year,p.month-1,p.day,p.hour||0,p.minute||0,p.second||0);
export function dateInZone(ms, zone) { const p=localParts(ms,zone);return `${p.year}-${String(p.month).padStart(2,'0')}-${String(p.day).padStart(2,'0')}`; }
export function shiftDate(date, days) { return new Date(Date.parse(date+'T00:00:00Z')+days*DAY).toISOString().slice(0,10); }
export function zonedInstant(date, clock, zone, end = false) {
  const cacheKey=`${date}/${clock}/${zone}/${end}`;
  if(instantCache.has(cacheKey))return instantCache.get(cacheKey);
  if(instantCache.size>2048)instantCache.clear();
  const [year,month,day]=date.split('-').map(Number), [hour,minute]=clock.split(':').map(Number);
  const target=wall({year,month,day,hour,minute}); const offsetKey=`${date}/${zone}`;const offsets=offsetCache.get(offsetKey)||new Set();
  // Sample both sides of any DST transition; offsets may be fractional hours.
  if(!offsets.size){for (let h=-36;h<=36;h+=6) { const t=Date.UTC(year,month-1,day,12)+h*3600000;offsets.add(wall(localParts(t,zone))-t); }
    if(offsetCache.size>128)offsetCache.clear();offsetCache.set(offsetKey,offsets);}
  const candidates=[...offsets].map(o=>target-o);
  const exact=candidates.filter(t=>wall(localParts(t,zone))===target).sort((a,b)=>a-b);
  // Fall-back overlap: earlier start, later end. Spring gap: shift forward by gap.
  if (exact.length) {const result=end?exact.at(-1):exact[0];instantCache.set(cacheKey,result);return result;}
  const after=candidates.filter(t=>wall(localParts(t,zone))>target).sort((a,b)=>wall(localParts(a,zone))-wall(localParts(b,zone)));
  if (!after.length) throw new Error('Unresolvable local date/time');
  instantCache.set(cacheKey,after[0]);return after[0];
}
export function windows(timing, from, to, maximum = 64) {
  if (timing.type==='once') return timing.endAtMs>from && timing.startAtMs<to ? [{startAtMs:timing.startAtMs,endAtMs:timing.endAtMs}] : [];
  const result=[]; let date=shiftDate(dateInZone(from,timing.timeZone),-1);
  if(timing.startDate&&date<timing.startDate)date=timing.startDate;
  const last=shiftDate(dateInZone(to,timing.timeZone),1);
  for (let n=0;date<=last && n<370;n++,date=shiftDate(date,1)) {
    if (timing.startDate && date<timing.startDate || timing.endDate && date>timing.endDate) continue;
    const day=(new Date(date+'T00:00:00Z').getUTCDay()+6)%7+1;
    if (!timing.daysOfWeek.includes(day)) continue;
    const endDate=timing.endTime<timing.startTime?shiftDate(date,1):date;
    const startAtMs=zonedInstant(date,timing.startTime,timing.timeZone);
    const endAtMs=zonedInstant(endDate,timing.endTime,timing.timeZone,true);
    if (endAtMs>startAtMs && endAtMs>from && startAtMs<to) {result.push({startAtMs,endAtMs});if(result.length>=maximum)break;}
  }
  return result;
}
export const paused = (item, time) => item?.pausedUntilMs===-1 || item?.pausedUntilMs>time;
export function evaluateRule(rule, presets, time) {
  const preset=presets.find(p=>p.id===rule.presetId);
  if (rule.deletedAtMs || preset?.deletedAtMs) return {phase:'deleted',windows:[]};
  if (rule.disabled || preset?.disabled) return {phase:'unavailable',windows:[]};
  let base=time;
  if (rule.timing.type==='weekly' && rule.timing.startDate) base=Math.max(base,Date.parse(rule.timing.startDate+'T00:00:00Z')-DAY);
  const upcoming=windows(rule.timing,time,Math.max(time+9*DAY,base+9*DAY),2);
  if (!upcoming.length) return {phase:'expired',windows:[]};
  if (paused(rule,time) || paused(preset,time)) return {phase:'paused',windows:upcoming};
  const active=upcoming.find(w=>w.startAtMs<=time && w.endAtMs>time);
  return {phase:active?'active':upcoming.length?'scheduled':'expired',windows:upcoming,active};
}

