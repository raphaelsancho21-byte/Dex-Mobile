package com.dex.security

import android.app.*
import android.content.*
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean

/** User-visible heartbeat agent. No stealth behavior or uninstall interception. */
class MonitorService:Service() {
    private val running=AtomicBoolean(false)
    private var worker:Thread?=null
    override fun onBind(intent:Intent?):IBinder?=null
    private val channel="dex_monitor"
    override fun onCreate(){super.onCreate();
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(channel,"DEX monitoramento",NotificationManager.IMPORTANCE_LOW))
    }
    private fun notice(msg:String):Notification {
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this,channel).setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("DEX Mobile ativo").setContentText(msg).setContentIntent(open).setOngoing(true).build()
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action=="STOP"){stopSelf();return START_NOT_STICKY}
        startForeground(7,notice("Monitorando conexão com seu notebook"))
        if(running.compareAndSet(false,true)){
            worker=Thread {
                val client=DexClient(this)
                var previous=""
                while(running.get()){
                    try {
                        val cm=getSystemService(ConnectivityManager::class.java)
                        val network=cm.activeNetwork
                        val caps=cm.getNetworkCapabilities(network)
                        val kind=when { caps==null->"Sem rede";caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)->"Wi-Fi";caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)->"Dados móveis";else->"Outra rede" }
                        val battery=getSystemService(BATTERY_SERVICE) as BatteryManager
                        val pct=battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                        if(previous.isNotEmpty()&&previous!=kind){client.event("NETWORK_CHANGE","Conexão alterada de $previous para $kind")}
                        previous=kind
                        client.heartbeat(kind,pct)
                    }catch(_:Exception){ /* Perda temporária de rede não é prova de ataque. */ }
                    for(i in 1..15){if(!running.get())break;SystemClock.sleep(1000)}
                }
            }.also{it.name="dex-heartbeat";it.isDaemon=true;it.start()}
        }
        return START_STICKY
    }
    override fun onDestroy(){running.set(false);worker?.interrupt();super.onDestroy()}
}
