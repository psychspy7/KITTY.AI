package com.kitty.ai;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseUser;
import org.json.JSONObject;
import okhttp3.*;
import okio.BufferedSource;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;

/** HTTPS gateway only. Firebase credentials are refreshed off the UI thread. */
final class BrainClient {
    private static final OkHttpClient HTTP=new OkHttpClient.Builder().connectTimeout(8,TimeUnit.SECONDS).readTimeout(50,TimeUnit.SECONDS).callTimeout(65,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build();
    private static final OkHttpClient SPEECH=HTTP.newBuilder().callTimeout(25,TimeUnit.SECONDS).build();
    private static final MediaType JSON=MediaType.get("application/json; charset=utf-8");
    static final class Session {
        final String url,uid,pairing; final boolean cloud=true; final FirebaseUser user;
        Session(Prefs p){url=p.url();user=KittyApp.auth()==null?null:KittyApp.auth().getCurrentUser();uid=user==null?"":user.getUid();pairing=ConversationStore.pairing(url,uid,uid.isEmpty());}
        boolean current(Prefs p){return !uid.isEmpty()&&uid.equals(p.accountId())&&url.equals(p.url());}
    }
    interface Events {void event(String kind,JSONObject data);void failed(String message);}
    static void validateUrl(String base) throws IOException {
        try{URI u=new URI(base);if(!"https".equals(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||!u.getPath().isEmpty())throw new Exception();}
        catch(Exception e){throw new IOException("KITTY service setup is pending. Contact Virat.");}
    }
    private static Request requestFor(Session account,String path,JSONObject payload) throws Exception {
        validateUrl(account.url);if(account.user==null)throw new IOException("Sign in with Google to continue.");
        String token=Tasks.await(account.user.getIdToken(false),15,TimeUnit.SECONDS).getToken();
        FirebaseUser now=KittyApp.auth().getCurrentUser();if(now==null||!account.uid.equals(now.getUid()))throw new IOException("Your account changed. Please retry.");
        Request.Builder b=new Request.Builder().url(account.url+path).header("Authorization","Bearer "+token).header("Accept","application/json");
        if(payload!=null)b.post(RequestBody.create(payload.toString(),JSON));return b.build();
    }
    private static void check(Response r) throws IOException {
        if(r.code()==401)throw new IOException("Your Google session needs attention. Sign out and sign in again.");
        if(r.code()==403)throw new IOException("This account cannot access that setting.");
        if(r.code()==429)throw new IOException("KITTY is busy. Please wait a moment and retry.");
        if(r.code()==400&&r.body()!=null){BufferedSource source=r.body().source();source.request(4001);if(source.getBuffer().size()<=4000){try{throw new IOException(new JSONObject(source.readUtf8()).optString("error","Check your fields."));}catch(org.json.JSONException ignored){}}}
        if(!r.isSuccessful()||r.body()==null)throw new IOException("KITTY service could not complete this request ("+r.code()+").");
    }
    static JSONObject request(Prefs p,String path,JSONObject payload) throws Exception{return request(new Session(p),path,payload);}
    static JSONObject request(Session account,String path,JSONObject payload) throws Exception {
        try(Response r=HTTP.newCall(requestFor(account,path,payload)).execute()){
            check(r);BufferedSource s=r.body().source();s.request(256001);if(s.getBuffer().size()>256000)throw new IOException("Response too large");return new JSONObject(s.readUtf8());
        }
    }
    static Call speechCall(Session account,String text) throws Exception{return SPEECH.newCall(requestFor(account,"/v1/speech",new JSONObject().put("text",text)));}
    static Call stream(Session account,String text,String session,String id,Events listener) throws Exception {
        Call call=HTTP.newCall(requestFor(account,"/v1/chat/stream",new JSONObject().put("text",text).put("session",session).put("request_id",id)));
        call.enqueue(new Callback(){
            public void onFailure(Call c,IOException e){listener.failed(c.isCanceled()?"Stopped":"Could not reach KITTY. Check your internet and retry.");}
            public void onResponse(Call c,Response r){
                try(Response response=r){
                    check(response);BufferedSource s=response.body().source();String kind="";long total=0;boolean done=false;
                    while(!s.exhausted()&&!c.isCanceled()){
                        String line=s.readUtf8LineStrict(100000);total+=line.length();if(total>2000000)throw new IOException("Response too large");
                        if(line.startsWith("event:"))kind=line.substring(6).trim();
                        else if(line.startsWith("data:")){JSONObject event=new JSONObject(line.substring(5).trim());if(kind.equals("error"))throw new IOException("KITTY could not finish that reply. Please retry.");listener.event(kind,event);if(kind.equals("done")){done=true;break;}}
                    }
                    if(!done&&!c.isCanceled())throw new IOException("The reply was interrupted. Please retry.");
                }catch(Exception e){if(!c.isCanceled())listener.failed(e.getMessage()==null?"Connection unavailable":e.getMessage());}
            }
        });return call;
    }
}
