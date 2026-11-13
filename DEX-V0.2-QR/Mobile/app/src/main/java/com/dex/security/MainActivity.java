package com.dex.security;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.ViewGroup;
import android.view.Gravity;
import android.os.Handler;
import android.os.Looper;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import android.util.Base64;
import org.json.JSONObject;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private TextView status;
    private Button scan, check, reset;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;


    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("dex_demo", Context.MODE_PRIVATE);
        LinearLayout root = new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(34,65,34,30);root.setBackgroundColor(Color.rgb(7,16,24));
        TextView title = new TextView(this); title.setText("⬡ DEX MOBILE");title.setTextSize(27);title.setTextColor(Color.rgb(50,220,175));root.addView(title);
        TextView subtitle = new TextView(this);subtitle.setText("V0.2 • Pareamento via QR Code");subtitle.setTextColor(Color.LTGRAY);subtitle.setTextSize(16);root.addView(subtitle);
        status = new TextView(this);status.setTextColor(Color.WHITE);status.setTextSize(17);status.setPadding(0,52,0,36);root.addView(status);
        scan = new Button(this);scan.setText("ESCANEAR QR DO PC");root.addView(scan,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        check = new Button(this);check.setText("VERIFICAR CONEXÃO");root.addView(check);
        reset = new Button(this);reset.setText("ESQUECER PAREAMENTO");root.addView(reset);
        TextView info = new TextView(this);info.setText("Protótipo LAN: não use em redes públicas nem para guardar informações sensíveis.");info.setTextColor(Color.GRAY);info.setPadding(0,50,0,0);root.addView(info);
        setContentView(root);
        scan.setOnClickListener(v->{ IntentIntegrator integrator = new IntentIntegrator(this); integrator.setPrompt("Escaneie o código da central DEX"); integrator.setBeepEnabled(false); integrator.setOrientationLocked(false); integrator.initiateScan(); });
        check.setOnClickListener(v->reconnect());
        reset.setOnClickListener(v->{prefs.edit().clear().apply();setStatus("Pareamento local esquecido.");});
        if(prefs.contains("id")) reconnect(); else setStatus("Sem pareamento. Escaneie o QR da central no PC.");
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) { if (result.getContents() == null) setStatus("Leitura cancelada."); else pair(result.getContents()); }
        else super.onActivityResult(requestCode,resultCode,data);
    }
    private void setStatus(String s) {handler.post(()->status.setText(s));}
    private String post(String base,String endpoint,JSONObject data) throws Exception {
        URL url=new URL(base+endpoint);
        if(!"http".equals(url.getProtocol())) throw new Exception("Protocolo inesperado");
        HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setRequestMethod("POST");c.setConnectTimeout(4500);c.setReadTimeout(4500);c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");
        try(OutputStream out=c.getOutputStream()){out.write(data.toString().getBytes(StandardCharsets.UTF_8));}
        int code=c.getResponseCode();if(code<200||code>=300) throw new Exception("Servidor retornou HTTP "+code);
        try(java.io.InputStream in=c.getInputStream();ByteArrayOutputStream b=new ByteArrayOutputStream()) {byte[] buf=new byte[2048];int n;while((n=in.read(buf))!=-1)b.write(buf,0,n);return b.toString("UTF-8");}finally{c.disconnect();}
    }
    private static String hex(int bytes){byte[] b=new byte[bytes];new SecureRandom().nextBytes(b);StringBuilder s=new StringBuilder();for(byte c:b)s.append(String.format(java.util.Locale.US,"%02x",c&255));return s.toString();}
    private void pair(String qr){
        worker.execute(()->{
            try{
                JSONObject p=new JSONObject(qr);
                if(!"DEX_PAIR_V02".equals(p.optString("type")))throw new Exception("QR não é do DEX");
                String base=p.getString("url");String token=p.getString("token");
                if(!base.matches("http://(192\\.168\\.\\d{1,3}\\.\\d{1,3}|10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|172\\.(1[6-9]|2\\d|3[01])\\.\\d{1,3}\\.\\d{1,3}):[0-9]{2,5}")) throw new Exception("Endereço não é LAN privada");
                String claim=hex(32);
                JSONObject req=new JSONObject().put("token",token).put("claim",claim).put("deviceName",android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL);
                JSONObject response=new JSONObject(post(base,"/api/pair/request",req));
                String requestId=response.getString("requestId");setStatus("Pedido enviado. Aprove o telefone na central do PC...");
                for(int i=0;i<40;i++){
                    Thread.sleep(1500);
                    JSONObject r=new JSONObject(post(base,"/api/pair/finalize",new JSONObject().put("requestId",requestId).put("claim",claim)));
                    if("denied".equals(r.optString("status")))throw new Exception("PC negou acesso");
                    if("approved".equals(r.optString("status"))){
                        // DEMO ONLY. For production replace with Android Keystore + secure TLS transport.
                        prefs.edit().putString("url",base).putString("id",r.getString("deviceId")).putString("secret",r.getString("deviceSecret")).apply();
                        setStatus("Pareamento aprovado! Use Verificar conexão.");return;
                    }
                }
                throw new Exception("Tempo de aprovação esgotado");
            }catch(Exception e){setStatus("Falha: "+e.getMessage());}
        });
    }
    private void reconnect(){worker.execute(()->{try{
        String base=prefs.getString("url","");String id=prefs.getString("id","");String secret=prefs.getString("secret","");
        if(base.isEmpty()||id.isEmpty()||secret.isEmpty())throw new Exception("Não existe vínculo salvo");
        JSONObject r=new JSONObject(post(base,"/api/device/check",new JSONObject().put("deviceId",id).put("deviceSecret",secret)));
        setStatus(r.optBoolean("connected")?"DEX PC conectado! (teste LAN)":"O PC não reconhece este vínculo. Escaneie outro QR.");
    }catch(Exception e){setStatus("PC indisponível: "+e.getMessage());}});}
    @Override protected void onDestroy(){super.onDestroy();worker.shutdownNow();}
}
