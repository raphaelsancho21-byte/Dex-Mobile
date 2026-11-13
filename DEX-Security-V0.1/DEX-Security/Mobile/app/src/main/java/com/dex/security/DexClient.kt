package com.dex.security

import android.content.Context
import org.json.JSONObject
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** TLS pinned to fingerprint confirmed on the PC's screen during pairing. */
class DexClient(private val context: Context) {
    private val prefs = context.getSharedPreferences("dex_connection",Context.MODE_PRIVATE)
    fun isPaired() = !prefs.getString("token", "").isNullOrBlank()
    fun host()=prefs.getString("host", "") ?: ""
    fun fingerprint()=prefs.getString("fingerprint", "") ?: ""
    private fun normalize(s:String)=s.replace(Regex("[^0-9A-Fa-f]"),"").uppercase()
    private fun digest(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02X".format(it)}
    private fun conn(endpoint:String): HttpsURLConnection {
        val fp=normalize(fingerprint())
        require(fp.length==64) { "Impressão digital TLS inválida" }
        val trust=object:X509TrustManager {
            override fun getAcceptedIssuers():Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain:Array<X509Certificate>,authType:String) { throw java.security.cert.CertificateException("Não usado") }
            override fun checkServerTrusted(chain:Array<X509Certificate>,authType:String) {
                if(chain.isEmpty() || digest(chain[0].encoded)!=fp)throw java.security.cert.CertificateException("Certificado diferente do notebook pareado")
            }
        }
        val ctx=SSLContext.getInstance("TLS");ctx.init(null, arrayOf<TrustManager>(trust),SecureRandom())
        val c=(URL("https://${host()}:38443$endpoint").openConnection() as HttpsURLConnection)
        c.sslSocketFactory=ctx.socketFactory
        // Host identity is verified by the explicitly pinned certificate fingerprint.
        c.hostnameVerifier=javax.net.ssl.HostnameVerifier { _,session ->
            try { val cert=session.peerCertificates[0] as X509Certificate;digest(cert.encoded)==fp } catch (_:Exception){false}
        }
        c.connectTimeout=6000;c.readTimeout=10000;c.setRequestProperty("Cache-Control","no-store")
        val token=prefs.getString("token", "") ?: ""
        if(token.isNotEmpty())c.setRequestProperty("Authorization","Bearer $token")
        return c
    }
    fun pair(ip:String,fp:String,pin:String):String {
        require(ip.matches(Regex("(?:\\d{1,3}\\.){3}\\d{1,3}"))) { "Digite o IPv4 do notebook" }
        require(normalize(fp).length==64) { "Copie o fingerprint TLS inteiro" }
        require(pin.matches(Regex("\\d{6}"))) { "O PIN deve conter 6 dígitos" }
        prefs.edit().putString("host",ip).putString("fingerprint",normalize(fp)).apply()
        try {
            val body=post("/pair",JSONObject().put("name","Samsung Android").put("pin",pin),false)
            val token=body.getString("token");prefs.edit().putString("token",token).apply()
            return "Pareamento realizado."
        } catch(e:Exception){prefs.edit().clear().apply();throw e}
    }
    fun post(endpoint:String,json:JSONObject,withToken:Boolean=true):JSONObject {
        val c=conn(endpoint);c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/json")
        c.outputStream.use{it.write(json.toString().toByteArray(Charsets.UTF_8))}
        val result=(if(c.responseCode in 200..299)c.inputStream else c.errorStream).bufferedReader().use{it.readText()}
        if(c.responseCode !in 200..299)throw IllegalStateException("Servidor ${c.responseCode}: $result")
        return JSONObject(result)
    }
    fun heartbeat(network:String,battery:Int):JSONObject=post("/heartbeat",JSONObject().put("network",network).put("battery",battery))
    fun event(type:String,message:String)=post("/event",JSONObject().put("type",type).put("message",message))
    fun updateBytes(maxBytes:Int=100*1024*1024):ByteArray {
        val c=conn("/update");if(c.responseCode!=200)throw IllegalStateException("Nenhum APK disponível (${c.responseCode})")
        if(c.contentLengthLong>maxBytes)throw IllegalStateException("APK grande demais")
        return c.inputStream.use { input -> val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);var total=0;while(true){val n=input.read(buffer);if(n<0)break;total+=n;if(total>maxBytes)throw IllegalStateException("Limite de arquivo excedido");output.write(buffer,0,n)};output.toByteArray() }
    }
    fun hash(bytes:ByteArray)=digest(bytes)
    fun clear(){prefs.edit().clear().apply()}
}
