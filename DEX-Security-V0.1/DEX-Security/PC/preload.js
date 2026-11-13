'use strict';
const {contextBridge,ipcRenderer}=require('electron');
contextBridge.exposeInMainWorld('dex',{
  status:()=>ipcRenderer.invoke('status'),
  rotatePin:()=>ipcRenderer.invoke('rotatePin'),
  selectUpdate:()=>ipcRenderer.invoke('selectUpdate'),
  revoke:id=>ipcRenderer.invoke('revoke',id),
  onChange:cb=>ipcRenderer.on('changed',cb)
});
