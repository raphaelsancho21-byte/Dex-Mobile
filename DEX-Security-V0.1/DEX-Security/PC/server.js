'use strict';
const https=require('https'), crypto=require('crypto'),fs=require('fs'),os=require('os'),path=require('path');
const selfsigned=require('selfsigned');
const PORT=38443;
const keyDir=path.join(os.homedir(),'.dex-control-center');
fs.mkdirSync(keyDir,{recursive:true,mode:0o700});
const certFile=path.join(keyDir,'tls.json');
let identity;
if(fs.existsSync(certFile)){identity=JSON.parse(fs.readFileSync(certFile,'utf8'));}
else{
  const data=selfsigned.generate([{name:'commonName',value:'DEX Control Center'}],{keySize:2048,days:3650,algorithm:'sha256'});
  identity={private:data.private,cert:data.cert};
  fs.writeFileSync(certFile,JSON.stringify(identity),{mode:0o600});
}
const fingerprint=new crypto.X509Certificate(identity.cert).fingerprint256.replace(/:/g,'').toUpperCase();
let pin=String(crypto.randomInt(100000,1000000));
let pinExpiry=Date.now()+10*60*1000;
const clients=new Map(), events=[];
let update=null, rejected=0;
const pairAttempts=new Map();
const listeners=new Set();
function changed(){for(const cb of listeners){try{cb()}catch{}}}
function addresses(){return Object.values(os.networkInterfaces()).flat().filter(a=>a.family==='IPv4'&&!a.internal).map(a=>a.address)}
function send(res,status,obj){res.writeHead(status,{'content-type':'application/json; charset=utf-8','cache-control':'no-store'});res.end(JSON.stringify(obj))}
function log(event){events.unshift({time:new Date().toISOString(),...event});events.length=Math.min(events.length,100);changed()}
function readJson(req){return new Promise((resolve,reject)=>{let raw='';req.on('data',d=>{raw+=d;if(raw.length>16384){reject(new Error('too large'));req.destroy()}});req.on('end',()=>{try{resolve(JSON.parse(raw||'{}'))}catch(e){reject(e)}});req.on('error',reject)})}
function auth(req){const header=req.headers.authorization||'';const token=header.startsWith('Bearer ')?header.slice(7):'';if(!token)return null;const hashed=crypto.createHash('sha256').update(token).digest('hex');return [...clients.values()].find(c=>c.tokenHash===hashed)||null}
const server=https.createServer({key:identity.private,cert:identity.cert},async(req,res)=>{
  const url=new URL(req.url,'https://localhost');
  if(req.method==='POST'&&url.pathname==='/pair'){
    let body;try{body=await readJson(req)}catch{return send(res,400,{error:'JSON inválido'})}
    const remote=req.socket.remoteAddress||'unknown';
    const prior=pairAttempts.get(remote)||{count:0,until:0};
    if(prior.until>Date.now())return send(res,429,{error:'Muitas tentativas. Aguarde 10 minutos.'});
    if(Date.now()>pinExpiry||String(body.pin)!==pin){
      rejected++;prior.count++;if(prior.count>=5){prior.until=Date.now()+10*60*1000;prior.count=0}pairAttempts.set(remote,prior);
      return send(res,403,{error:'PIN inválido ou expirado'});
    }
    pairAttempts.delete(remote);
    if(typeof body.name!=='string'||body.name.length>64)return send(res,400,{error:'Nome inválido'});
    const token=crypto.randomBytes(32).toString('hex'),id=crypto.randomUUID();
    clients.set(id,{id,name:body.name,tokenHash:crypto.createHash('sha256').update(token).digest('hex'),lastSeen:Date.now(),status:'Pareado',network:'',battery:-1});
    pin=String(crypto.randomInt(100000,1000000));pinExpiry=Date.now()+10*60*1000;
    log({type:'PAIR',message:`${body.name} pareado`});send(res,200,{id,token});return;
  }
  const client=auth(req);
  if(!client)return send(res,401,{error:'Dispositivo não autorizado'});
  if(req.method==='POST'&&url.pathname==='/heartbeat'){
    let body;try{body=await readJson(req)}catch{return send(res,400,{error:'JSON inválido'})}
    client.lastSeen=Date.now();client.status='Online';client.network=String(body.network||'').slice(0,80);
    client.battery=Number.isInteger(body.battery)?Math.max(-1,Math.min(100,body.battery)):-1;
    changed();return send(res,200,{ok:true,serverTime:Date.now(),update:update?{name:update.name,sha256:update.sha256,size:update.data.length}:null});
  }
  if(req.method==='POST'&&url.pathname==='/event'){
    let body;try{body=await readJson(req)}catch{return send(res,400,{error:'JSON inválido'})}
    client.lastSeen=Date.now();log({type:String(body.type||'EVENT').slice(0,32),message:`${client.name}: ${String(body.message||'Evento').slice(0,200)}`});return send(res,200,{ok:true});
  }
  if(req.method==='GET'&&url.pathname==='/update'){
    if(!update)return send(res,404,{error:'Nenhuma atualização selecionada'});
    res.writeHead(200,{'content-type':'application/vnd.android.package-archive','content-length':update.data.length,'cache-control':'no-store'});res.end(update.data);return;
  }
  send(res,404,{error:'Rota desconhecida'});
});
server.on('error',e=>{console.error('DEX TLS:',e.message)});
function status(){return {port:PORT,ips:addresses(),fingerprint,pin:Date.now()<pinExpiry?pin:'Expirado — renovar',clients:[...clients.values()].map(({tokenHash,...c})=>({...c,online:Date.now()-c.lastSeen<45000})),events:events.slice(0,30),update:update?{name:update.name,sha256:update.sha256,size:update.data.length}:null,rejected}}
function rotatePin(){pin=String(crypto.randomInt(100000,1000000));pinExpiry=Date.now()+10*60*1000;changed()}
function setUpdate(file){const data=fs.readFileSync(file);if(data.length>100*1024*1024)throw new Error('APK excede 100 MB');if(!file.toLowerCase().endsWith('.apk'))throw new Error('Selecione um APK');update={name:path.basename(file),data,sha256:crypto.createHash('sha256').update(data).digest('hex')};log({type:'UPDATE',message:`APK preparado: ${update.name}`});changed();}
function revoke(id){if(clients.delete(id)){log({type:'REVOKE',message:`Pareamento ${id} revogado`});changed()}}
module.exports={start:()=>server.listen(PORT,'0.0.0.0'),status,onChange:fn=>{listeners.add(fn);return()=>listeners.delete(fn)},rotatePin,setUpdate,revoke};
