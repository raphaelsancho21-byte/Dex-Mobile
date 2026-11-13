package com.dex.security

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class MainActivity:Activity(){
    private lateinit var result:TextView
    private lateinit var client:DexClient
    private fun msg(s:String){runOnUiThread{result.text=s}}
    private fun field(hint:String):EditText=EditText(this).apply {this.hint=hint;setSingleLine();setTextColor(Color.WHITE);setHintTextColor(Color.LTGRAY);setPadding(10,20,10,20)}
    private fun button(name:String,block:()->Unit):Button=Button(this).apply{text=name;setOnClickListener{block()}}
    private fun task(block:()->Unit){Thread{try{block()}catch(e:Exception){msg("Erro: ${e.message}")}}.start()}
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
        client=DexClient(this)
        val pane=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(28,30,28,35);setBackgroundColor(Color.rgb(11,17,31))}
        val scroll=ScrollView(this);scroll.addView(pane);setContentView(scroll)
        val title=TextView(this).apply{text="DEX MOBILE  ·  V0.1";textSize=23f;setTextColor(Color.rgb(125,205,255));setPadding(0,0,0,18)};pane.addView(title)
        val subtitle=TextView(this).apply{text="Proteção local: protótipo de conectividade, eventos e atualização. Ainda não bloqueia invasores.";setTextColor(Color.WHITE);textSize=14f};pane.addView(subtitle)
        val ip=field("IP do notebook (ex.: 192.168.0.107)")
        val fp=field("Impressão digital TLS (64 caracteres)")
        val pin=field("PIN de 6 dígitos")
        pane.addView(ip);pane.addView(fp);pane.addView(pin)
        result=TextView(this).apply{text=if(client.isPaired())"Pareado: ${client.host()}" else "Aguardando pareamento";textSize=15f;setTextColor(Color.rgb(127,232,174));setPadding(4,24,4,24)}
        pane.addView(result)
        pane.addView(button("1. Parear com notebook") {task{msg(client.pair(ip.text.toString().trim(),fp.text.toString(),pin.text.toString().trim()))}})
        pane.addView(button("2. Iniciar monitoramento"){
            if(!client.isPaired()){msg("Faça o pareamento primeiro");return@button}
            if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),77)}
            try{startForegroundService(Intent(this,MonitorService::class.java));msg("Monitoramento iniciado (notificação persistente).") }catch(e:Exception){msg("Não foi possível iniciar: ${e.message}")}
        })
        pane.addView(button("3. Parar monitoramento"){stopService(Intent(this,MonitorService::class.java));msg("Monitoramento interrompido")})
        pane.addView(button("4. Enviar evento de teste") {task{client.event("TEST","Alerta de teste acionado no Samsung");msg("Evento de teste enviado ao notebook")}})
        pane.addView(button("5. Buscar APK pelo Update Bridge") {task{downloadUpdate()}})
        pane.addView(button("Desvincular este notebook") {stopService(Intent(this,MonitorService::class.java));client.clear();msg("Pareamento local apagado; revogue também no PC")})
        val foot=TextView(this).apply{text="Somente rede local. A versão 0.1 ainda NÃO implementa Last Stand, VPN, Tor, proteção de senhas nem antidésinstalação.";setTextColor(Color.LTGRAY);setPadding(0,30,0,0)};pane.addView(foot)
    }
    private fun downloadUpdate(){
        require(client.isPaired()){ "Pareie o notebook primeiro" }
        msg("Consultando atualização...")
        val current=client.heartbeat("Atualização solicitada",-1).optJSONObject("update")?:throw IllegalStateException("Nenhuma atualização disponível no PC")
        val data=client.updateBytes()
        val actual=client.hash(data);val expected=current.getString("sha256").uppercase()
        if(actual!=expected)throw SecurityException("SHA-256 incorreto: arquivo rejeitado")
        val dir=File(cacheDir,"updates");dir.mkdirs()
        val file=File(dir,"dex-update.apk");file.writeBytes(data)
        msg("Arquivo verificado. Solicitando instalador Android...")
        val uri=FileProvider.getUriForFile(this,"${packageName}.files",file)
        val install=Intent(Intent.ACTION_VIEW).apply{setDataAndType(uri,"application/vnd.android.package-archive");addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)}
        runOnUiThread{try{startActivity(install)}catch(e:Exception){msg("Instalação precisa de permissão do Android: ${e.message}. Vá em Configurações > Apps > Acesso especial > Instalar apps desconhecidos e autorize o DEX Mobile.")}}
    }
}
