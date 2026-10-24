import chromium from '@sparticuz/chromium';
import puppeteer from 'puppeteer-core';
import {lookup} from 'node:dns/promises';
import {isIP} from 'node:net';

const TESTS={
 wikipedia:{name:'Wikipedia · Multi-topic research',urls:['https://en.wikipedia.org/wiki/Artificial_intelligence','https://en.wikipedia.org/wiki/Machine_learning','https://en.wikipedia.org/wiki/Computer_vision'],selector:'#mw-content-text p'},
 hackernews:{name:'Hacker News · Three feeds',urls:['https://news.ycombinator.com/news','https://news.ycombinator.com/newest','https://news.ycombinator.com/show'],selector:'tr.athing'},
 mdn:{name:'MDN · Technical docs',urls:['https://developer.mozilla.org/en-US/docs/Web/JavaScript','https://developer.mozilla.org/en-US/docs/Web/API/Document_Object_Model','https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API'],selector:'main h1, main h2'},
 github:{name:'GitHub · Open source research',urls:['https://github.com/microsoft/playwright','https://github.com/puppeteer/puppeteer','https://github.com/browser-use/browser-use'],selector:'h1,article h2'}
};
const denyIp=ip=>{if(isIP(ip)===4){const p=ip.split('.').map(Number);return p[0]===0||p[0]===10||p[0]===127||p[0]>=224||(p[0]===169&&p[1]===254)||(p[0]===172&&p[1]>=16&&p[1]<=31)||(p[0]===192&&p[1]===168)||(p[0]===100&&p[1]>=64&&p[1]<=127);}const s=ip.toLowerCase();return s==='::'||s==='::1'||s.startsWith('fc')||s.startsWith('fd')||s.startsWith('fe80')||s.startsWith('::ffff:')&&denyIp(s.slice(7));};
async function safeUrl(raw){const u=new URL(raw);if(!['http:','https:'].includes(u.protocol)||u.username||u.password||u.port&&!['80','443'].includes(u.port))throw Error('Unsafe navigation URL');const h=u.hostname;if(!h.includes('.')||h.endsWith('.local')||h.endsWith('.internal')||h==='localhost')throw Error('Internal URL blocked');if(isIP(h)){if(denyIp(h))throw Error('Private network blocked');}else{const addresses=await lookup(h,{all:true});if(addresses.some(a=>denyIp(a.address)))throw Error('Private DNS blocked');}return u.toString();}
function parseJson(s){try{return JSON.parse(s);}catch{try{return JSON.parse(s.match(/\{[\s\S]*\}/)?.[0]||'');}catch{return {};}}}
function allowedApiUrl(raw){const u=new URL(raw);if(u.protocol!=='https:'||u.username||u.password||u.port||isIP(u.hostname)||u.hostname.endsWith('.local')||u.hostname.endsWith('.internal'))throw Error('Custom endpoint must be public HTTPS hostname');return u.toString();}
async function modelCall(cfg,{text,image,strictJson=false}){
 const {provider,model,apiKey,customUrl}=cfg;if(!apiKey||apiKey.length>1000)throw Error('Missing or invalid key');const started=Date.now();let response,raw;
 if(provider==='gemini'){
  const parts=[{text}];if(image)parts.push({inline_data:{mime_type:'image/jpeg',data:image}});
  response=await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent?key=${encodeURIComponent(apiKey)}`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({contents:[{role:'user',parts}],generationConfig:{temperature:0,maxOutputTokens:300,...(strictJson?{responseMimeType:'application/json'}:{})}}),signal:AbortSignal.timeout(35000),redirect:'error'});
  raw=await response.json();if(!response.ok)throw Error(`Gemini ${response.status}: ${JSON.stringify(raw.error||{}).slice(0,220)}`);
  return {text:raw.candidates?.[0]?.content?.parts?.map(x=>x.text||'').join('')||'',usage:raw.usageMetadata?.totalTokenCount??null,input:raw.usageMetadata?.promptTokenCount??null,output:raw.usageMetadata?.candidatesTokenCount??null,ms:Date.now()-started};
 }
 let url=provider==='meta'?'https://api.meta.ai/v1/chat/completions':provider==='openrouter'?'https://openrouter.ai/api/v1/chat/completions':provider==='custom'?allowedApiUrl(customUrl):null;
 if(!url)throw Error('Unsupported provider');
 const content=image?[{type:'text',text},{type:'image_url',image_url:{url:`data:image/jpeg;base64,${image}`}}]:text;
 response=await fetch(url,{method:'POST',headers:{Authorization:`Bearer ${apiKey}`,'Content-Type':'application/json'},body:JSON.stringify({model,temperature:0,max_tokens:300,messages:[{role:'user',content}]}),signal:AbortSignal.timeout(45000),redirect:'error'});
 raw=await response.json();if(!response.ok)throw Error(`${provider} ${response.status}: ${JSON.stringify(raw.error||{}).slice(0,220)}`);
 return {text:typeof raw.choices?.[0]?.message?.content==='string'?raw.choices[0].message.content:JSON.stringify(raw.choices?.[0]?.message?.content||''),usage:raw.usage?.total_tokens??null,input:raw.usage?.prompt_tokens??null,output:raw.usage?.completion_tokens??null,ms:Date.now()-started};
}
async function getEvidence(page,selector){return page.evaluate(sel=>{let nodes=[...document.querySelectorAll(sel)];const trim=t=>String(t||'').replace(/\s+/g,' ').trim();return {title:document.title,url:location.href,heading:trim(document.querySelector('h1')?.innerText).slice(0,150),count:nodes.length,items:nodes.slice(0,7).map(el=>trim(el.innerText).slice(0,120))};},selector);}
async function runCase(cfg,testId,mode){
 const spec=TESTS[testId],start=Date.now();let browser;let aiCalls=0,screenshots=0,domReads=0,actions=0,errors=0,visionCalls=0,visionFallbacks=0,inputTokens=0,outputTokens=0,reportedUsageCalls=0;let verified=false;const trace=[],observations=[];
 const call=async(label,payload)=>{const t=Date.now();aiCalls++;if(payload.image)visionCalls++;try{const r=await modelCall(cfg,payload);if(Number.isFinite(r.input)){inputTokens+=r.input;outputTokens+=r.output||0;reportedUsageCalls++;}trace.push({type:'model',label,ms:Date.now()-t,success:true,vision:!!payload.image});return r;}catch(e){errors++;trace.push({type:'model',label,ms:Date.now()-t,success:false,error:e.message,vision:!!payload.image});throw e;}};
 try{
  browser=await puppeteer.launch({args:chromium.args,executablePath:await chromium.executablePath(),headless:true,defaultViewport:{width:1060,height:720}});
  const page=await browser.newPage();await page.setRequestInterception(true);
  page.on('request',async req=>{try{if(['data:','blob:'].includes(new URL(req.url()).protocol))return req.continue();await safeUrl(req.url());req.continue();}catch{req.abort();}});
  for(let i=0;i<spec.urls.length;i++){
   const at=Date.now();const target=await safeUrl(spec.urls[i]);await page.goto(target,{waitUntil:'domcontentloaded',timeout:19000});actions++;
   let evidence=await getEvidence(page,spec.selector);domReads++;observations.push(evidence);
   trace.push({type:'browser',label:`Navigate ${i+1}/${spec.urls.length}`,ms:Date.now()-at,url:target,success:true});
   if(mode==='baseline'){
    const shot=await page.screenshot({type:'jpeg',quality:42,encoding:'base64'});screenshots++;
    const r=await call(`Vision checkpoint ${i+1}`,{image:shot,text:`You are visually checking a browser webpage screenshot. Task: research these three sites in order: ${spec.urls.join(' | ')}. This is page ${i+1}, URL ${target}. Read the visible page, report visible heading and whether page looks loaded. Return JSON with keys {"visibleHeading":string,"loaded":boolean,"next":"continue"}. Do NOT invent hidden content. This image is the observation.`,strictJson:true});
    const parsed=parseJson(r.text);if(typeof parsed.loaded!=='boolean')trace.push({type:'warning',label:'Vision response did not include loaded boolean',success:false});
   }else{
    if(!evidence.count){visionFallbacks++;const shot=await page.screenshot({type:'jpeg',quality:42,encoding:'base64'});screenshots++;
      await call(`Vision fallback ${i+1}`,{image:shot,text:`Page ${target} had no expected DOM elements. Visually identify whether page loaded or is blocked. Respond JSON {"loaded": boolean,"visibleHeading":string}.`});
    }
   }
  }
  await call('Evidence summary',{text:`Return compact JSON summarizing ONLY this captured structured DOM evidence, including any missing records: ${JSON.stringify(observations).slice(0,5000)}`,strictJson:true});
  verified=observations.length===spec.urls.length&&observations.every(o=>o.count>0&&o.heading);
  return {ok:true,mode,testId,testName:spec.name,verified,model:cfg.model,metrics:{durationMs:Date.now()-start,aiCalls,visionCalls,visionFallbacks,screenshots,actions,domReads,errors,inputTokens:reportedUsageCalls===aiCalls?inputTokens:null,outputTokens:reportedUsageCalls===aiCalls?outputTokens:null,reportedUsageCalls,verified},observations,trace,benchmarkKind:'guided_visual_vs_dom',notes:'Identical predetermined targets; visual baseline observes each page image but does not autonomously discover navigation links. Success verified independently from DOM.'};
 }finally{if(browser)await browser.close().catch(()=>{});}
}
export default async function handler(req,res){res.setHeader('Cache-Control','no-store');if(req.method!=='POST')return res.status(405).json({error:'POST only'});try{let {testId='wikipedia',mode='optimized',provider='meta',model='muse-spark-1.3',apiKey='',customUrl=''}=req.body||{};if(!TESTS[testId]||!['baseline','optimized'].includes(mode))return res.status(400).json({error:'Unknown mode or test'});if(typeof apiKey!=='string'||!apiKey)return res.status(400).json({error:'API key required'});return res.json(await runCase({provider,model:String(model).slice(0,160),apiKey,customUrl},testId,mode));}catch(e){return res.status(500).json({error:String(e.message||e).slice(0,350)});}}
