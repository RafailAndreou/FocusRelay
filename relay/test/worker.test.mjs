// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
import test from 'node:test';
import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import {readFileSync,readdirSync} from 'node:fs';
import worker, {blockArguments,digest,sign,verify,discoveryEvent} from '../src/worker.mjs';

class D1 {
  constructor() {this.db=new DatabaseSync(':memory:'); for(const f of readdirSync(new URL('../migrations/',import.meta.url)).filter(f=>f.endsWith('.sql')).sort())this.db.exec(readFileSync(new URL('../migrations/'+f,import.meta.url),'utf8'));}
  prepare(sql) {
    const statement=this.db.prepare(sql); let values=[];
    const api={bind(...args){values=args; return api;},async first(){return statement.get(...values)||null;},async run(){const r=statement.run(...values);return {meta:{changes:Number(r.changes)}};}};
    return api;
  }
  async batch(statements) { return Promise.all(statements.map(s=>s.run())); }
}
const origin='https://mindful.example.workers.dev';
const app={packageName:'com.instagram.android',label:'Instagram'};
function env(){return {DB:new D1(),OWNER_SECRET:'owner_'+ 'a'.repeat(40),DEVICE_TOKEN:'device_'+ 'b'.repeat(40),AUTH_SECRET:'auth_'+ 'c'.repeat(40)};}
function request(path,{method='GET',token,body,headers={}}={}){
  return new Request(origin+path,{method,headers:{...(token?{Authorization:'Bearer '+token}:{}),...(body?{'Content-Type':'application/json'}:{}),...headers},body:body===undefined?undefined:typeof body==='string'?body:JSON.stringify(body)});
}
async function sync(e,appliedRevision=-1,apps=[app],permissions=true){
  return worker.fetch(request('/device/sync',{method:'POST',token:e.DEVICE_TOKEN,body:{apps,appliedRevision,status:{enabled:true,usageAccess:permissions,overlay:permissions}}}),e);
}
async function block(e,until=new Date(Date.now()+3600000).toISOString()){
  return worker.fetch(request('/api/blocks',{method:'POST',token:e.OWNER_SECRET,body:{packages:[app.packageName],until}}),e);
}
test('private endpoints reject anonymous and wrong-role credentials',async()=>{
  const e=env();
  assert.equal((await worker.fetch(request('/api/status'),e)).status,401);
  assert.equal((await worker.fetch(request('/api/status',{token:e.DEVICE_TOKEN}),e)).status,401);
  assert.equal((await worker.fetch(request('/device/sync',{method:'POST',token:e.OWNER_SECRET,body:{}}),e)).status,401);
});
test('an offline block is pending, then becomes acknowledged after device applies revision',async()=>{
  const e=env(); await sync(e);
  const accepted=await (await block(e)).json(); assert.equal(accepted.applied,false); assert.equal(accepted.revision,1);
  let state=await (await worker.fetch(request('/api/status',{token:e.OWNER_SECRET}),e)).json(); assert.equal(state.acknowledged,false);
  const fetched=await (await sync(e)).json(); assert.equal(fetched.blocks[0].packageName,app.packageName);
  await sync(e,fetched.revision);
  state=await (await worker.fetch(request('/api/status',{token:e.OWNER_SECRET}),e)).json(); assert.equal(state.acknowledged,true);
});
test('unknown packages, overlong blocks, timezone-free dates and expired dates are rejected',async()=>{
  const time=Date.now(); const valid={packages:[app.packageName],until:new Date(time+60000).toISOString()};
  for(const args of [{...valid,packages:['com.not.allowed']},{...valid,until:new Date(time+8*86400000).toISOString()},{...valid,until:'2026-10-20T14:00:00'},{...valid,until:new Date(time-1000).toISOString()}]) assert.throws(()=>blockArguments(args,[app],time));
});
test('block replay preserves absolute expiry and shorter requests do not shorten existing blocks',async()=>{
  const e=env();await sync(e);
  const until=new Date(Date.now()+3600000).toISOString();await block(e,until);await block(e,until);await block(e,new Date(Date.now()+60000).toISOString());
  const state=await (await sync(e)).json();assert.equal(state.blocks.length,1);assert.equal(state.blocks[0].untilMs,Date.parse(until));
});
test('unblock removes only desired remote blocks',async()=>{
  const e=env();await sync(e);await block(e);
  const r=await worker.fetch(request('/api/unblock',{method:'POST',token:e.OWNER_SECRET,body:{packages:[app.packageName]}}),e);
  assert.equal(r.status,202);assert.deepEqual((await r.json()).blocks,[]);
});
test('cross-origin writes are rejected and credentials are not echoed',async()=>{
  const e=env();const r=await worker.fetch(request('/api/blocks',{method:'POST',token:e.OWNER_SECRET,headers:{Origin:'https://evil.example'},body:{}}),e);
  assert.equal(r.status,403);assert.ok(!(await r.text()).includes(e.OWNER_SECRET));
});
test('malformed and oversized request bodies fail cleanly',async()=>{
  const e=env();
  assert.equal((await worker.fetch(request('/api/blocks',{method:'POST',token:e.OWNER_SECRET,body:'{bad'}),e)).status,400);
  assert.equal((await worker.fetch(request('/api/blocks',{method:'POST',token:e.OWNER_SECRET,body:'x'.repeat(140000)}),e)).status,413);
});
test('MCP initializes, lists scoped tools, and rejects tampered access tokens',async()=>{
  const e=env();const token=await sign({kind:'access',aud:origin,scope:'mindful:control',exp:Date.now()+60000},e.AUTH_SECRET);
  const rpc=body=>worker.fetch(request('/mcp',{method:'POST',token,body}),e);
  const init=await (await rpc({jsonrpc:'2.0',id:1,method:'initialize',params:{protocolVersion:'2025-06-18'}})).json();assert.equal(init.result.protocolVersion,'2025-06-18');
  const listing=await (await rpc({jsonrpc:'2.0',id:2,method:'tools/list'})).json();assert.equal(listing.result.tools.length,34);assert.equal(listing.result.tools[0].securitySchemes[0].type,'oauth2');
  assert.equal((await worker.fetch(request('/mcp',{method:'POST',token:token+'x',body:{jsonrpc:'2.0',id:1,method:'ping'}}),e)).status,401);
});
test('signed tokens reject incorrect audience, incorrect key, expiry and malformed input',async()=>{
  const e=env();assert.equal(await verify('junk',e.AUTH_SECRET),null);
  assert.equal(await verify(await sign({exp:Date.now()-1},e.AUTH_SECRET),e.AUTH_SECRET),null);
  const token=await sign({kind:'access',aud:'https://other.example',scope:'mindful:control',exp:Date.now()+60000},e.AUTH_SECRET);
  assert.equal(await verify(token,'different-key'),null);
  assert.equal((await worker.fetch(request('/api/status',{token}),e)).status,401);
});
for (const redirect of ['https://chatgpt.com/connector_platform_oauth_redirect','https://chatgpt.com/connector/oauth/test-client','https://claude.ai/api/mcp/auth_callback']) {
test('OAuth registration, consent, PKCE, replay and refresh: '+redirect,async()=>{
  const e=env();
  const registration=await worker.fetch(request('/register',{method:'POST',body:{redirect_uris:[redirect],token_endpoint_auth_method:'none'}}),e);
  assert.equal(registration.status,201);const {client_id}=await registration.json();
  const verifier='v'.repeat(43);const challenge=await digest(verifier);
  const query=new URLSearchParams({client_id,redirect_uri:redirect,response_type:'code',scope:'mindful:control',resource:origin,state:'test-state',code_challenge:challenge,code_challenge_method:'S256'});
  const page=await worker.fetch(request('/authorize?'+query),e);assert.equal(page.status,200);
  const cookie=page.headers.get('Set-Cookie').split(';')[0];const html=await page.text();const ticket=html.match(/name="ticket" value="([^"]+)"/)[1];
  const consent=await worker.fetch(request('/authorize',{method:'POST',body:new URLSearchParams({ticket,password:e.OWNER_SECRET}).toString(),headers:{Origin:origin,Cookie:cookie,'Content-Type':'application/x-www-form-urlencoded'}}),e);
  assert.equal(consent.status,303);const callback=new URL(consent.headers.get('Location'));assert.equal(callback.searchParams.get('state'),'test-state');assert.equal(callback.searchParams.get('iss'),origin);
  const params={grant_type:'authorization_code',code:callback.searchParams.get('code'),client_id,redirect_uri:redirect,resource:origin,code_verifier:verifier};
  const exchange=p=>worker.fetch(request('/token',{method:'POST',body:new URLSearchParams(p).toString()}),e);
  assert.equal((await exchange({...params,code_verifier:'x'.repeat(43)})).status,400);
  const tokenResponse=await exchange(params);assert.equal(tokenResponse.status,200);const tokens=await tokenResponse.json();
  assert.equal((await exchange(params)).status,400);
  assert.equal((await worker.fetch(request('/api/status',{token:tokens.access_token}),e)).status,200);
  const refresh={grant_type:'refresh_token',refresh_token:tokens.refresh_token,client_id,resource:origin};
  assert.equal((await exchange(refresh)).status,200);assert.equal((await exchange(refresh)).status,400);
});
}
test('OAuth registration rejects arbitrary and lookalike callbacks',async()=>{
  for(const uri of ['https://evil.example/callback','https://chatgpt.com.evil.example/connector_platform_oauth_redirect','http://chatgpt.com/connector_platform_oauth_redirect','https://chatgpt.com/connector_platform_oauth_redirect?next=evil',
    'https://claude.ai.evil.example/api/mcp/auth_callback','http://claude.ai/api/mcp/auth_callback',
    'https://claude.ai/api/mcp/auth_callback?next=evil','https://claude.ai/api/mcp/auth_callback#fragment',
    'https://claude.ai/api/mcp/auth_callback/','https://claude.ai/other',
    'https://attacker@claude.ai/api/mcp/auth_callback','https://attacker@chatgpt.com/connector_platform_oauth_redirect']) {
    const e=env();
    assert.equal((await worker.fetch(request('/register',{method:'POST',body:{redirect_uris:[uri]}}),e)).status,400);
  }
});
for (const [clientName,redirect] of [['ChatGPT','https://chatgpt.com/connector_platform_oauth_redirect'],['Claude','https://claude.ai/api/mcp/auth_callback']]) {
test('browser consent CSRF and redirect policy: '+clientName,async()=>{
  const e=env();
  const registration=await worker.fetch(request('/register',{method:'POST',body:{redirect_uris:[redirect],token_endpoint_auth_method:'none'}}),e);
  const {client_id}=await registration.json();
  const query=new URLSearchParams({client_id,redirect_uri:redirect,response_type:'code',scope:'mindful:control',resource:origin,state:'browser-test',code_challenge:await digest('v'.repeat(43)),code_challenge_method:'S256'});
  const page=await worker.fetch(request('/authorize?'+query),e);
  assert.equal(page.headers.get('Referrer-Policy'),'same-origin');
  const policy=page.headers.get('Content-Security-Policy');
  assert.ok(policy.includes("form-action 'self' "+new URL(redirect).origin+";"));
  assert.ok(policy.includes("frame-ancestors 'none'"));
  const cookie=page.headers.get('Set-Cookie').split(';')[0];
  const html=await page.text();
  assert.ok(html.includes("Authorize "+clientName+"</button>"));
  const ticket=html.match(/name="ticket" value="([^"]+)"/)[1];
  const submit=(password,headers)=>worker.fetch(request('/authorize',{method:'POST',body:new URLSearchParams({ticket,password}).toString(),headers}),e);
  assert.equal((await submit(e.OWNER_SECRET,{Origin:'null',Cookie:cookie})).status,403);
  assert.equal((await submit(e.OWNER_SECRET,{Origin:origin})).status,403);
  assert.equal((await submit('incorrect',{Origin:origin,Cookie:cookie})).status,403);
  const consent=await submit(' \n'+e.OWNER_SECRET+'\n ',{Origin:origin,Cookie:cookie});
  assert.equal(consent.status,303);
  assert.equal(new URL(consent.headers.get('Location')).origin,new URL(redirect).origin);
  assert.equal(consent.headers.get('Referrer-Policy'),'no-referrer');
});
}
test('login and registration attempts are rate limited',async()=>{
  const e=env();let result;for(let i=0;i<11;i++) result=await worker.fetch(request('/register',{method:'POST',body:{redirect_uris:['https://chatgpt.com/connector_platform_oauth_redirect']}}),e);
  assert.equal(result.status,429);
});
test('missing phone permissions invalidate even an incorrectly reported acknowledgment',async()=>{
  const e=env();await sync(e);await block(e);
  await sync(e,1,[app],false);
  const state=await (await worker.fetch(request('/api/status',{token:e.OWNER_SECRET}),e)).json();
  assert.equal(state.appliedRevision,-1);assert.equal(state.acknowledged,false);assert.equal(state.phone.overlay,false);
});
test('MCP tool errors are explicit and notifications have no response body',async()=>{
  const e=env();const token=await sign({kind:'access',aud:origin,scope:'mindful:control',exp:Date.now()+60000},e.AUTH_SECRET);
  const r=await worker.fetch(request('/mcp',{method:'POST',token,body:{jsonrpc:'2.0',id:3,method:'tools/call',params:{name:'block_apps',arguments:{packages:['com.unknown'],until:new Date(Date.now()+60000).toISOString()}}}}),e);
  assert.equal((await r.json()).result.isError,true);
  const notification=await worker.fetch(request('/mcp',{method:'POST',token,body:{jsonrpc:'2.0',method:'notifications/initialized'}}),e);
  assert.equal(notification.status,202);assert.equal(await notification.text(),'');
});

test('temporary discovery diagnostics redact credentials, arguments and query strings',async()=>{
 const secret='diagnostic-secret-not-for-logs';
 const req=request('/mcp?secret='+secret,{method:'POST',token:secret,headers:{Origin:'https://chatgpt.com',Cookie:secret},body:{jsonrpc:'2.0',id:1,method:'initialize',params:{protocolVersion:'2025-06-18',clientInfo:{name:secret},password:secret}}});
 const event=await discoveryEvent(req,401);
 assert.deepEqual(event,{event:'mindful_mcp_discovery',path:'/mcp',httpMethod:'POST',status:401,authorizationPresent:true,origin:'chatgpt',rpcMethod:'initialize',protocolVersion:'2025-06-18'});
 assert.ok(!JSON.stringify(event).includes(secret));
 const call=await discoveryEvent(request('/mcp',{method:'POST',token:secret,body:{jsonrpc:'2.0',method:'tools/call',params:{arguments:{packages:[secret]}}}}),200);
 assert.ok(!JSON.stringify(call).includes(secret));
 assert.equal(await discoveryEvent(request('/device/sync',{method:'POST',token:secret,body:{apps:[secret]}}),200),null);
 const unknown=await discoveryEvent(request('/mcp',{method:'POST',body:{method:secret}}),400);
 assert.equal(unknown.rpcMethod,'other');assert.ok(!JSON.stringify(unknown).includes(secret));
});

