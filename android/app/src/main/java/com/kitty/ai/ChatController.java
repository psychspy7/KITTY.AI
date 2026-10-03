package com.kitty.ai;

import android.app.Activity;
import android.content.*;
import android.os.*;
import org.json.*;
import okhttp3.Call;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Process-owned conversation state survives rotation and a closed activity. */
final class ChatController {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService disk=Executors.newSingleThreadExecutor(),network=Executors.newFixedThreadPool(2);
    private final ConversationStore store;final Prefs prefs;final SpeechOutput speaker;
    final List<JSONObject> turns=new ArrayList<>();private final Set<Runnable> observers=new HashSet<>();
    WeakReference<Activity> foreground=new WeakReference<>(null);
    boolean busy,loaded,voiceRunning;String phase="Ready",voiceState="Off",heard="";
    float micLevel;
    private JSONObject active;private Call call;private StringBuilder reply=new StringBuilder();private int spoken;private boolean stoppingSpeech;
    private int viewGeneration;
    private final AtomicBoolean syncing=new AtomicBoolean();
    ChatController(Context app){
        prefs=new Prefs(app);store=new ConversationStore(app);speaker=new SpeechOutput(app);speaker.changed=this::changed;
        BrainClient.Session first=new BrainClient.Session(prefs);String firstSession=prefs.session();int firstGeneration=viewGeneration;disk.execute(()->{store.recover();List<JSONObject> old=store.recent(firstSession,first.pairing,first.cloud);main.post(()->{if(firstGeneration!=viewGeneration||!first.current(prefs))return;turns.addAll(old);loaded=true;changed();sync();});});
    }
    void observe(Runnable r){observers.add(r);r.run();}void remove(Runnable r){observers.remove(r);}
    void changed(){for(Runnable r:new ArrayList<>(observers))r.run();}
    void voice(String state,String words,boolean running){voiceState=state;if(words!=null)heard=words;voiceRunning=running;changed();}
    void note(String message){phase=message;changed();}
    void history(boolean all){if(busy)return;loaded=false;int own=++viewGeneration;BrainClient.Session account=new BrainClient.Session(prefs);String session=all?null:prefs.session();disk.execute(()->{List<JSONObject> old=store.recent(session,account.pairing,account.cloud);main.post(()->{if(own!=viewGeneration||!account.current(prefs))return;turns.clear();turns.addAll(old);loaded=true;changed();sync();});});}
    void accountChanged(){stop();turns.clear();history(false);}
    void newChat(){if(busy)return;viewGeneration++;prefs.newSession();turns.clear();loaded=true;changed();}
    private void save(JSONObject turn){try{JSONObject copy=new JSONObject(turn.toString());disk.execute(()->store.save(copy));}catch(JSONException e){throw new IllegalStateException(e);}}
    boolean send(Context source,String text,String via){
        text=text.trim();if(text.equalsIgnoreCase("stop")||text.equalsIgnoreCase("stop talking")){stop();return true;}
        if(!loaded||busy||text.isEmpty()||text.length()>8000){note(busy?"Finish or stop this reply first, Sir.":"Use a message under 8,000 characters, Sir.");return false;}
        if(prefs.cloud()&&prefs.accountId().isEmpty()){note("Sign in with Google in Settings first, Sir.");return false;}
        speaker.stop();stoppingSpeech=false;JSONObject turn=new JSONObject();
        try{turn.put("id",UUID.randomUUID().toString()).put("session",prefs.session()).put("input",text).put("reply","").put("mode","pending").put("source",via).put("created",System.currentTimeMillis()).put("pairing",store.pairing());}catch(JSONException e){return false;}
        turns.add(turn);active=turn;busy=true;phase=prefs.cloud()?"Connecting to KITTY":"Connecting to laptop";save(turn);changed();
        if(text.matches("(?i)(?:introduce (?:yourself|urself|urslef)|who (?:are (?:you|u)|created (?:you|u)|made (?:you|u))|what is your name)[?.!]*")){
            finish(turn,"Sir, I'm KITTY AI, created by Virat. I help with conversations, memories and phone commands—with a little wit. "+(prefs.cloud()?"Groq powers my cloud brain.":"Qwen supplies my local language model."),"identity");return true;
        }
        if(prefs.cloud()){
            String input=text,owner=turn.optString("pairing");disk.execute(()->{String answer=store.memoryCommand(owner,input);main.post(()->{if(active!=turn)return;if(answer!=null)finish(turn,answer,"memory");else sendRemoteOrAction(source,input,turn);});});return true;
        }
        sendRemoteOrAction(source,text,turn);return true;
    }
    private void sendRemoteOrAction(Context source,String text,JSONObject turn){
        Action action=Router.parse(text);
        if(action!=null){phase="Phone command";Actions.execute(source,action,answer->{if(active==turn)finish(turn,answer,"local");if(source instanceof ActionActivity)((ActionActivity)source).complete();});return;}
        reply=new StringBuilder();spoken=0;speaker.beginCloud();
        try{call=BrainClient.stream(prefs,text,turn.optString("session"),turn.optString("id"),new BrainClient.Events(){
            public void event(String kind,JSONObject data){main.post(()->{
                if(active!=turn)return;
                if(kind.equals("status")){phase=data.optString("phase","Thinking");changed();}
                else if(kind.equals("token")){reply.append(data.optString("text"));put(turn,"reply",reply.toString());phase="Receiving reply";speakSentences(false);changed();}
                else if(kind.equals("done")){put(turn,"metadata",data.toString());finish(turn,data.optString("reply"),data.optString("mode","model"));}
            });}
            public void failed(String message){main.post(()->{if(active==turn)finish(turn,reply.length()>0?reply+"\n[Interrupted] "+message:message,"error");});}
        });}catch(Exception e){finish(turn,e.getMessage(),"error");}
    }
    private void speakSentences(boolean last){
        if(stoppingSpeech)return;
        String value=reply.toString();int end=spoken;
        for(int i=spoken;i<value.length();i++)if((value.charAt(i)=='.'||value.charAt(i)=='!'||value.charAt(i)=='?'||value.charAt(i)=='\n')&&(i+1==value.length()||Character.isWhitespace(value.charAt(i+1))))end=i+1;
        if(last)end=value.length();
        if(end>spoken&&(last||end-spoken>=(prefs.cloud()?120:18))){speaker.enqueue(value.substring(spoken,end));spoken=end;}
    }
    private void finish(JSONObject turn,String answer,String mode){
        put(turn,"reply",answer==null?"Sir, no reply was returned.":answer);put(turn,"mode",mode);save(turn);
        if(!stoppingSpeech){
            if(mode.equals("model")&&reply.length()>0){speakSentences(true);speaker.end();}
            else {speaker.say(mode.equals("web")?"Sir, I've found web results. The excerpts and source links are in your chat.":turn.optString("reply"));}
        }else speaker.end();
        active=null;call=null;busy=false;phase=mode.equals("error")?"Connection needs attention":"Ready";changed();sync();
    }
    static void put(JSONObject object,String key,Object value){try{object.put(key,value);}catch(JSONException e){throw new IllegalStateException(e);}}
    void stop(){
        stoppingSpeech=true;speaker.stop();
        if(active==null){phase="Stopped speaking";changed();return;}
        JSONObject turn=active;String id=turn.optString("id");if(call!=null)call.cancel();
        active=null;call=null;busy=false;put(turn,"reply",turn.optString("reply")+" [Stopped]");put(turn,"mode","cancelled");save(turn);phase="Stopped";changed();
        BrainClient.Session account=new BrainClient.Session(prefs);network.execute(()->{try{BrainClient.request(account,"/v1/cancel",new JSONObject().put("request_id",id));}catch(Exception ignored){}sync();});
    }
    void feedback(String id,int rating,String correction,Runnable saved){
        disk.execute(()->{store.feedback(id,rating,correction);main.post(saved);sync();});
    }
    void sync(){
        if(!loaded||prefs.token().isEmpty()||!syncing.compareAndSet(false,true))return;
        BrainClient.Session account=new BrainClient.Session(prefs);
        disk.execute(()->{
            JSONArray pending=store.pending(account.pairing),memories=account.cloud?store.memoryPending(account.pairing):new JSONArray();List<JSONObject> feedback=store.feedbackPending(account.pairing);
            if(!account.cloud&&pending.length()==0&&feedback.isEmpty()){syncing.set(false);return;}
            network.execute(()->{
                boolean success=false;
                try{
                    if(account.cloud){JSONArray rows=BrainClient.request(account,"/v1/memories",new JSONObject().put("changes",memories)).getJSONArray("memories");disk.execute(()->store.memoryMerged(account.pairing,memories,rows));}
                    if(pending.length()>0){JSONArray accepted=BrainClient.request(account,"/v1/events",new JSONObject().put("events",pending)).getJSONArray("accepted");disk.execute(()->store.synced(accepted));}
                    for(JSONObject f:feedback){BrainClient.request(account,"/v1/feedback",f);disk.execute(()->store.feedbackSynced(f));}
                    success=true;
                }catch(Exception ignored){}finally{
                    syncing.set(false);boolean drain=success&&(pending.length()==20||memories.length()>0);main.post(()->{if(drain||!account.current(prefs))sync();});
                }
            });
        });
    }
}
