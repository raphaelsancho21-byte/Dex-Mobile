'use strict';
const $=id=>document.getElementById(id);
function clear(el){el.replaceChildren()}
function text(tag,s,cls){const e=document.createElement(tag);e.textContent=s;if(cls)e.className=cls;return e}
async function refresh(){
 const s=await window.dex.status();$('pin').textContent=s.pin;$('ips').textContent=s.ips.join('   •   ')||'Nenhum endereço IPv4';$('fingerprint').textContent=s.fingerprint.match(/.{1,4}/g).join(' ');
 const devices=$('devices');clear(devices);
 if(!s.clients.length)devices.append(text('li','Nenhum celular pareado'));
 for(const c of s.clients){const li=document.createElement('li'),row=document.createElement('div');row.className='row';
 row.append(text('span',`${c.online?'🟢':'⚪'} ${c.name} · ${c.online?'Online':'Desconectado'}`));const b=text('button','Revogar','danger');b.addEventListener('click',()=>{if(confirm('Revogar acesso deste celular?'))window.dex.revoke(c.id).then(refresh)});row.append(b);li.append(row);li.append(text('small',`Rede: ${c.network||'—'} · Bateria: ${c.battery>=0?c.battery+'%':'—'}`));devices.append(li)}
 $('updateInfo').textContent=s.update?`${s.update.name} (${(s.update.size/1024/1024).toFixed(1)} MB)`:'Nenhuma atualização';
 const events=$('events');clear(events);if(!s.events.length)events.append(text('li','Nenhum evento recebido'));
 for(const e of s.events)events.append(text('li',`${new Date(e.time).toLocaleString('pt-BR')} · ${e.type}: ${e.message}`));
}
$('renew').addEventListener('click',()=>window.dex.rotatePin().then(refresh));
$('update').addEventListener('click',async()=>{const r=await window.dex.selectUpdate();if(r.error)alert(r.error);await refresh()});
window.dex.onChange(refresh);setInterval(refresh,6000);refresh();
