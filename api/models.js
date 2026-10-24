import {lookup} from 'node:dns/promises';
import {isIP} from 'node:net';
const err=(status,msg)=>Object.assign(new Error(msg),{status});
function isBlocked(ip){if(ip.startsWith('::ffff:'))return isBlocked(ip.slice(7));if(isIP(ip)===4){const [a,b,c]=ip.split('.').map(Number);return a===0||a===10||a===127||a>=224||(a===169&&b===254)||(a===172&&b>=16&&b<=31)||(a===192&&b===168)||(a===100&&b>=64&&b<=127)||(a===198&&(b===18||b===19));}return ip==='::'||ip==='::1'||/^(fc|fd|fe80|ff|2001:db8)/i.test(ip)}
async function customEndpoint(raw){let u;try{u=new URL(raw)}catch{throw err(400,'Custom URL غير صالح')};if(u.protocol!=='https:'||u.username||u.password||u.port||!u.hostname.includes('.')||u.hostname.endsWith('.local')||u.hostname.endsWith('.internal')||u.hostname.endsWith('.localhost')||u.hostname.endsWith('.test')||u.hostname.endsWith('.invalid')||u.hostname.endsWith('.onion'))throw err(400,'Custom API يجب أن يكون HTTPS عام');const ips=isIP(u.hostname)?[{address:u.hostname}]:await lookup(u.hostname,{all:true});if(!ips.length||ips.some(x=>isBlocked(x.address)))throw err(403,'عنوان API خاص غير مسموح');return u}
export default async function handler(req,res){res.setHeader('Cache-Control','no-store');if(req.method!=='POST')return res.status(405).json({error:'POST only'});try{
const {provider,apiKey,customUrl}=req.body||{};
if(typeof apiKey!=='string'||apiKey.length<2||apiKey.length>1000)throw err(400,'أدخل مفتاح API');
let url, headers={'Authorization':`Bearer ${apiKey}`};
if(provider==='gemini'){url='https://generativelanguage.googleapis.com/v1beta/models?pageSize=100';headers={'x-goog-api-key':apiKey};}
else if(provider==='meta')url='https://api.meta.ai/v1/models';
else if(provider==='openrouter')url='https://openrouter.ai/api/v1/models';
else if(provider==='custom'){const u=await customEndpoint(customUrl);u.pathname=u.pathname.replace(/\/chat\/completions\/?$/,'/models');url=u.toString();}
else throw err(400,'Provider غير معروف');
let entries=[], nextUrl=url;
for(let n=0;n<(provider==='gemini'?5:1)&&nextUrl;n++){
const r=await fetch(nextUrl,{headers,redirect:'error',signal:AbortSignal.timeout(18000)});
const body=await r.json().catch(()=>({}));if(!r.ok)throw err(502,body.error?.message||`HTTP ${r.status}`);
const arr=provider==='gemini'?body.models:body.data;
if(!Array.isArray(arr))throw err(502,'الاستجابة لا تحتوي قائمة نماذج');
entries.push(...arr.map(m=>({id:String(provider==='gemini'?(m.name||'').replace(/^models\//,''):m.id||m.name||''),name:String(m.displayName||m.name||m.id||''),description:String(m.description||'').slice(0,180)})).filter(m=>m.id));
if(provider==='gemini'&&body.nextPageToken){const next=new URL(url);next.searchParams.set('pageToken',body.nextPageToken);nextUrl=next.toString()}else nextUrl=null;
}
res.status(200).json({models:entries.slice(0,1500),total:entries.length});
}catch(e){res.status(e.status||500).json({error:e.message||'خطأ أثناء جلب النماذج'})}}
