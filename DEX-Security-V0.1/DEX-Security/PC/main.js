'use strict';
const {app,BrowserWindow,ipcMain,dialog}=require('electron');
const path=require('path'),backend=require('./server');
let window;
app.whenReady().then(()=>{
  backend.start();
  window=new BrowserWindow({width:1080,height:760,minWidth:780,minHeight:580,backgroundColor:'#0b1020',webPreferences:{preload:path.join(__dirname,'preload.js'),contextIsolation:true,nodeIntegration:false,sandbox:true}});
  window.removeMenu();window.loadFile('index.html');
  backend.onChange(()=>{if(window&&!window.isDestroyed())window.webContents.send('changed')});
});
ipcMain.handle('status',()=>backend.status());
ipcMain.handle('rotatePin',()=>backend.rotatePin());
ipcMain.handle('revoke',(_,id)=>backend.revoke(id));
ipcMain.handle('selectUpdate',async()=>{
 const selection=await dialog.showOpenDialog(window,{properties:['openFile'],filters:[{name:'Android APK',extensions:['apk']}]});
 if(selection.canceled)return {cancelled:true};
 try{backend.setUpdate(selection.filePaths[0]);return {ok:true}}catch(e){return {error:e.message}}
});
app.on('window-all-closed',()=>{app.quit()});
