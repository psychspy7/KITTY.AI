package com.kitty.ai;

import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.UUID;

public final class BrainClient {
    public static void validateUrl(String base) throws Exception {
        URI u=new URI(base);
        if(u.getHost()==null || u.getUserInfo()!=null || u.getQuery()!=null || u.getFragment()!=null || (u.getPath()!=null && !u.getPath().isEmpty() && !u.getPath().equals("/"))) throw new Exception("Use only the server address and port.");
        if("https".equals(u.getScheme()))return;
        if(!"http".equals(u.getScheme()))throw new Exception("Use http:// for USB/LAN or https:// for a trusted secure server.");
        String h=u.getHost();
        if(!h.matches("\\d{1,3}(?:\\.\\d{1,3}){3}"))throw new Exception("Plain HTTP needs a private numeric IPv4 address, such as 127.0.0.1.");
        String[] parts=h.split("\\.");int a=Integer.parseInt(parts[0]), b=Integer.parseInt(parts[1]);
        for(String part:parts)if(Integer.parseInt(part)>255)throw new Exception("Invalid IP address.");
        if(!(a==127 || a==10 || a==192&&b==168 || a==172&&b>=16&&b<=31 || a==100&&b>=64&&b<=127))throw new Exception("Use HTTPS outside USB, private LAN, or your private VPN.");
    }
    public static JSONObject request(Prefs prefs,String path,JSONObject payload) throws Exception {
        validateUrl(prefs.url());
        if(prefs.token().isEmpty())throw new Exception("Pair with the laptop in Settings first, Sir.");
        URL url=new URL(prefs.url().replaceAll("/+$", "")+path);
        HttpURLConnection c=(HttpURLConnection)url.openConnection();
        c.setInstanceFollowRedirects(false);c.setConnectTimeout(7000);c.setReadTimeout(140000);
        c.setRequestProperty("Authorization","Bearer "+prefs.token());
        try {
            if(payload!=null){
                byte[] data=payload.toString().getBytes(StandardCharsets.UTF_8);
                c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");c.setFixedLengthStreamingMode(data.length);
                try(java.io.OutputStream out=c.getOutputStream()){out.write(data);}
            }
            int status=c.getResponseCode();
            if(status==401)throw new Exception("Pairing token doesn't match, Sir. Check Settings.");
            if(status!=200)throw new Exception("Laptop returned HTTP "+status+", Sir.");
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int n;
                while((n=in.read(buffer))!=-1){if(out.size()+n>200000)throw new Exception("Reply too large");out.write(buffer,0,n);}
                return new JSONObject(out.toString("UTF-8"));
            }
        } finally {c.disconnect();}
    }
    public static JSONObject chat(Prefs p,String text) throws Exception {
        return request(p,"/v1/chat",new JSONObject().put("text",text).put("session",p.session()).put("request_id",UUID.randomUUID().toString()));
    }
}
