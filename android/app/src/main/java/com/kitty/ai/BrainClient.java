package com.kitty.ai;

import org.json.JSONObject;
import okhttp3.*;
import okio.BufferedSource;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;

/** Independent, cancellable network calls: a reply never blocks Stop or status. */
public final class BrainClient {
    private static final OkHttpClient HTTP=new OkHttpClient.Builder().connectTimeout(7,TimeUnit.SECONDS).readTimeout(150,TimeUnit.SECONDS).callTimeout(180,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build();
    private static final OkHttpClient LOCAL=HTTP.newBuilder().proxy(java.net.Proxy.NO_PROXY).build();
    private static final OkHttpClient SPEECH=HTTP.newBuilder().callTimeout(20,TimeUnit.SECONDS).readTimeout(18,TimeUnit.SECONDS).build();
    private static final MediaType JSON=MediaType.get("application/json; charset=utf-8");
    /** Capture credentials before queuing work; an account switch must never retarget an outbox. */
    static final class Session {
        final String url,token,pairing;final boolean cloud;
        Session(Prefs p){url=p.url();token=p.token();cloud=p.cloud();pairing=ConversationStore.pairing(url,cloud?p.accountId():token,token.isEmpty());}
        boolean current(Prefs p){return url.equals(p.url())&&token.equals(p.token());}
    }
    public interface Events { void event(String kind,JSONObject data); void failed(String message); }
    public static void validateUrl(String base) throws Exception {
        URI u=new URI(base);
        if(u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||(u.getPath()!=null&&!u.getPath().isEmpty()&&!u.getPath().equals("/")))throw new Exception("Use only the server address and port.");
        if("https".equals(u.getScheme()))return;
        if(!"http".equals(u.getScheme())||!"127.0.0.1".equals(u.getHost()))throw new Exception("Use http://127.0.0.1:8765 over USB, or HTTPS with a trusted certificate for Wi-Fi.");
    }
    private static Request requestFor(Prefs p,String path,JSONObject payload) throws Exception {
        return requestFor(new Session(p),path,payload);
    }
    private static Request requestFor(Session account,String path,JSONObject payload) throws Exception {
        validateUrl(account.url);String token=account.token;if(token.isEmpty())throw new Exception(account.cloud?"Sign in with Google first, Sir.":"Pair with the laptop in Settings first, Sir.");
        Request.Builder b=new Request.Builder().url(account.url.replaceAll("/+$","")+path).header("Authorization","Bearer "+token);
        if(payload!=null)b.post(RequestBody.create(payload.toString(),JSON));return b.build();
    }
    private static Call newCall(Request request){return (request.url().host().equals("127.0.0.1")?LOCAL:HTTP).newCall(request);}
    private static void check(Response r) throws IOException {
        if(r.code()==401)throw new IOException("Your session expired or pairing changed, Sir. Sign in again or check Settings.");
        if(r.code()==404)throw new IOException("Update the KITTY server to v0.4 and restart it, Sir.");
        if(!r.isSuccessful()||r.body()==null)throw new IOException("KITTY server returned HTTP "+r.code()+", Sir.");
    }
    public static JSONObject request(Prefs p,String path,JSONObject payload) throws Exception {
        return request(new Session(p),path,payload);
    }
    static JSONObject request(Session account,String path,JSONObject payload) throws Exception {
        try(Response r=newCall(requestFor(account,path,payload)).execute()){
            check(r);BufferedSource s=r.body().source();s.request(256001);
            if(s.getBuffer().size()>256000)throw new IOException("Reply too large");return new JSONObject(s.readUtf8());
        }
    }
    public static JSONObject publicRequest(Prefs p,String path,JSONObject payload) throws Exception {
        validateUrl(p.url());if(!p.url().startsWith("https://"))throw new IOException("Google accounts need the admin's HTTPS service address.");
        Request.Builder b=new Request.Builder().url(p.url().replaceAll("/+$","")+path);
        if(payload!=null)b.post(RequestBody.create(payload.toString(),JSON));
        try(Response r=newCall(b.build()).execute()){
            BufferedSource s=r.body()==null?null:r.body().source();if(s==null)throw new IOException("Service returned no response");s.request(64001);if(s.getBuffer().size()>64000)throw new IOException("Service response too large");JSONObject body=new JSONObject(s.readUtf8());if(!r.isSuccessful())throw new IOException(body.optString("error","Sign-in service unavailable"));return body;
        }
    }
    public static byte[] speech(Prefs p,String text) throws Exception {
        try(Response r=speechCall(p,text).execute()){
            check(r);BufferedSource s=r.body().source();s.request(5000001);if(s.getBuffer().size()>5000000)throw new IOException("Speech too large");return s.readByteArray();
        }
    }
    static Call speechCall(Prefs p,String text) throws Exception {return speechCall(new Session(p),text);}
    static Call speechCall(Session account,String text) throws Exception {return SPEECH.newCall(requestFor(account,"/v1/speech",new JSONObject().put("text",text)));}
    public static Call stream(Prefs p,String text,String session,String id,Events listener) throws Exception {
        JSONObject body=new JSONObject().put("text",text).put("session",session).put("request_id",id);
        Call call=newCall(requestFor(p,"/v1/chat/stream",body));
        call.enqueue(new Callback(){
            public void onFailure(Call c,IOException e){android.util.Log.w("KittyNetwork","Connection failed: "+e.getClass().getSimpleName());listener.failed(c.isCanceled()?"Stopped":"Sir, I couldn't reach the KITTY server. Check your internet, service address and sign-in in Settings.");}
            public void onResponse(Call c,Response r){
                try(Response response=r){
                    check(response);BufferedSource s=response.body().source();String kind="";long total=0;boolean done=false;
                    while(!s.exhausted()){
                        String line=s.readUtf8LineStrict(100000);total+=line.length();if(total>2000000)throw new IOException("Reply too large");
                        if(line.startsWith("event:"))kind=line.substring(6).trim();
                        else if(line.startsWith("data:")){
                            JSONObject event=new JSONObject(line.substring(5).trim());
                            if(kind.equals("error"))throw new IOException(event.optString("error","Laptop error"));
                            listener.event(kind,event);if(kind.equals("done")){done=true;break;}
                        }
                    }
                    if(!done&&!c.isCanceled())throw new IOException("Sir, the connection ended before the reply completed. Please retry.");
                }catch(Exception e){if(!c.isCanceled())listener.failed(e.getMessage()==null?"Sir, the laptop connection failed.":e.getMessage());}
            }
        });return call;
    }
}
