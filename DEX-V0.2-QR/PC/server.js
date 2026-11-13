'use strict';
// DEX QR V0.2 - local network prototype. NOT production-ready remote security.
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const crypto = require('node:crypto');
const QRCode = require('qrcode');
const PORT = Number(process.env.DEX_PORT || 4343);
const pairings = new Map();
const devices = new Map(); // Demonstration only: resets when PC server restarts.
const uid = () => crypto.randomBytes(24).toString('hex');
const json = (res, code, value) => { res.writeHead(code, {'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store','Access-Control-Allow-Origin':'http://127.0.0.1:'+PORT}); res.end(JSON.stringify(value)); };
function localOnly(req) { const a = req.socket.remoteAddress || ''; return a === '127.0.0.1' || a === '::1' || a === '::ffff:127.0.0.1'; }
function lanIp() { for (const list of Object.values(os.networkInterfaces())) for (const n of list || []) if (n.family === 'IPv4' && !n.internal) return n.address; return '127.0.0.1'; }
function body(req) { return new Promise((resolve,reject)=> {let s='';req.on('data',c=>{s+=c;if(s.length>8192) { reject(new Error('Body too large'));req.destroy(); }});req.on('end',()=>{try{resolve(JSON.parse(s||'{}'));}catch{reject(new Error('Invalid JSON'));}});req.on('error',reject);}); }
const safeEqual = (a,b) => typeof a === 'string' && typeof b === 'string' && a.length===b.length && crypto.timingSafeEqual(Buffer.from(a),Buffer.from(b));
const cleanup = () => {const now=Date.now(); for(const [k,p] of pairings) if (p.deadline<now) pairings.delete(k);};
const server = http.createServer(async(req,res)=>{
 try {
  cleanup();
  const u = new URL(req.url, 'http://localhost');
  if (req.method === 'OPTIONS') return json(res,200,{});
  if (req.method === 'GET' && (u.pathname === '/' || u.pathname === '/app.js' || u.pathname === '/style.css')) {
   if (!localOnly(req)) return json(res,403,{error:'PC UI only available on localhost'});
   const file = ({'/':'index.html','/app.js':'app.js','/style.css':'style.css'})[u.pathname];
   res.writeHead(200,{'Content-Type':file.endsWith('.js')?'text/javascript':file.endsWith('.css')?'text/css':'text/html','Cache-Control':'no-store'});
   return fs.createReadStream(path.join(__dirname,'public',file)).pipe(res);
  }
  if (req.method === 'POST' && u.pathname === '/api/admin/create') {
   if (!localOnly(req)) return json(res,403,{error:'Only PC may generate QR'});
   const token=uid(); const deadline=Date.now()+60000; pairings.set(token,{deadline,state:'new'});
   const payload=JSON.stringify({type:'DEX_PAIR_V02',url:`http://${lanIp()}:${PORT}`,token});
   const qr=await QRCode.toDataURL(payload,{errorCorrectionLevel:'M',width:320,margin:2});
   return json(res,200,{qr,expiresAt:deadline,ip:lanIp()});
  }
  if (req.method === 'POST' && u.pathname === '/api/pair/request') {
   const b=await body(req);const p=pairings.get(b.token);
   if(!p || p.state!=='new' || p.deadline<Date.now()) return json(res,400,{error:'Invalid, used, or expired QR'});
   if (typeof b.claim !== 'string' || !/^[a-f0-9]{64}$/.test(b.claim)) return json(res,400,{error:'Invalid claim'});
   p.state='pending';p.claimHash=crypto.createHash('sha256').update(b.claim).digest('hex');p.deviceName=String(b.deviceName||'Android').slice(0,60);p.requestId=uid();
   return json(res,200,{requestId:p.requestId});
  }
  if (req.method === 'GET' && u.pathname === '/api/admin/pending') {
   if(!localOnly(req)) return json(res,403,{error:'PC only'});
   return json(res,200,{pending:[...pairings.values()].filter(p=>p.state==='pending').map(p=>({requestId:p.requestId,deviceName:p.deviceName}))});
  }
  if (req.method === 'POST' && u.pathname === '/api/admin/decision') {
   if(!localOnly(req)) return json(res,403,{error:'PC only'});
   const b=await body(req);const p=[...pairings.values()].find(p=>p.requestId===b.requestId && p.state==='pending');
   if(!p) return json(res,404,{error:'Request not found'});
   if (b.approve===true) {p.state='approved';p.deviceId=uid();p.secret=uid();devices.set(p.deviceId,{name:p.deviceName,secret:p.secret});}
   else p.state='denied';
   return json(res,200,{ok:true});
  }
  if (req.method === 'POST' && u.pathname === '/api/pair/finalize') {
   const b=await body(req);const p=[...pairings.values()].find(p=>p.requestId===b.requestId);
   if(!p || typeof b.claim!=='string' || !safeEqual(p.claimHash||'',crypto.createHash('sha256').update(b.claim).digest('hex'))) return json(res,403,{error:'Unauthorized'});
   if(p.state==='pending') return json(res,200,{status:'pending'});
   if(p.state==='denied') return json(res,200,{status:'denied'});
   if(p.state!=='approved') return json(res,400,{error:'Pairing unavailable'});
   p.state='completed';
   return json(res,200,{status:'approved',deviceId:p.deviceId,deviceSecret:p.secret});
  }
  if (req.method === 'POST' && u.pathname === '/api/device/check') {
   const b=await body(req);const d=devices.get(b.deviceId);
   return json(res,200,{connected:!!d && safeEqual(d.secret,b.deviceSecret)});
  }
  if (req.method === 'GET' && u.pathname === '/api/admin/devices') {
   if(!localOnly(req)) return json(res,403,{error:'PC only'});
   return json(res,200,{devices:[...devices].map(([id,d])=>({id,name:d.name}))});
  }
  if(req.method==='POST' && u.pathname==='/api/admin/revoke') {
   if(!localOnly(req))return json(res,403,{error:'PC only'});
   const b=await body(req); devices.delete(b.deviceId);return json(res,200,{ok:true});
  }
  json(res,404,{error:'Not found'});
 } catch(e) {if(!res.headersSent)json(res,500,{error:e.message||'Server error'});}
});
server.listen(PORT,'0.0.0.0',()=>console.log(`DEX PC: http://127.0.0.1:${PORT} | Android: http://${lanIp()}:${PORT}`));
