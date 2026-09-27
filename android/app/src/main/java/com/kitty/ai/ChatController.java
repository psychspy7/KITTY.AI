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
    private JSONObject active;private Call call;private StringBuilder reply=new StringBuilder();private int spoken;private boolean stoppingSpeech;
    private final AtomicBoolean syncing=new AtomicBoolean();
    ChatController(Context app){
        prefs=new Prefs(app);store=new ConversationStore(app);speaker=new SpeechOutput(app);speaker.changed=this::changed;
        disk.execute(()->{store.recover();List<JSONObject> old=store.recent(prefs.session());main.post(()->{turns.addAll(old);loaded=true;changed();sync();});});
    }
    void observe(Runnable r){observers.add(r);r.run();}void remove(Runnable r){observers.remove(r);}
    void changed(){for(Runnable r:new ArrayList<>(observers))r.run();}
    void voice(String state,String words,boolean running){voiceState=state;if(words!=null)heard=words;voiceRunning=running;changed();}
    void note(String message){phase=message;changed();}
    void history(boolean all){if(busy)return;loaded=false;disk.execute(()->{List<JSONObject> old=store.recent(all?null:prefs.session());main.post(()->{turns.clear();turns.addAll(old);loaded=true;changed();});});}
    void newChat(){if(busy)return;prefs.newSession();turns.clear();changed();}
    private void save(JSONObject turn){try{JSONObject copy=new JSONObject(turn.toString());disk.execute(()->store.save(copy));}catch(JSONException e){throw new IllegalStateException(e);}}
    boolean send(Context source,String text,String via){
        text=text.trim();if(text.equalsIgnoreCase("stop")||text.equalsIgnoreCase("stop talking")){stop();return true;}
        if(!loaded||busy||text.isEmpty()||text.length()>8000){note(busy?"Finish or stop this reply first, Sir.":"Use a message under 8,000 characters, Sir.");return false;}
        speaker.stop();stoppingSpeech=false;JSONObject turn=new JSONObject();
        try{turn.put("id",UUID.randomUUID().toString()).put("session",prefs.session()).put("input",text).put("reply","").put("mode","pending").put("source",via).put("created",System.currentTimeMillis());}catch(JSONException e){return false;}
        turns.add(turn);active=turn;busy=true;phase="Connecting to laptop";save(turn);changed();
        if(text.matches("(?i)(?:introduce (?:yourself|urself|urslef)|who (?:are (?:you|u)|created (?:you|u)|made (?:you|u))|what is your name)[?.!]*")){
            finish(turn,"Sir, I'm KITTY AI, Virat's personal AI assistant. Virat created the KITTY project; Qwen supplies my underlying language model. I help with conversations, memories and phone commands—with a little wit.","identity");return true;
        }
        Action action=Router.parse(text);
        if(action!=null){phase="Phone command";Actions.execute(source,action,answer->{if(active==turn)finish(turn,answer,"local");if(source instanceof ActionActivity)((ActionActivity)source).complete();});return true;}
        reply=new StringBuilder();spoken=0;speaker.begin();
        try{call=BrainClient.stream(prefs,text,turn.optString("session"),turn.optString("id"),new BrainClient.Events(){
            public void event(String kind,JSONObject data){main.post(()->{
                if(active!=turn)return;
                if(kind.equals("status")){phase=data.optString("phase","Thinking");changed();}
                else if(kind.equals("token")){reply.append(data.optString("text"));put(turn,"reply",reply.toString());phase="Receiving reply";speakSentences(false);changed();}
                else if(kind.equals("done")){finish(turn,data.optString("reply"),data.optString("mode","model"));}
            });}
            public void failed(String message){main.post(()->{if(active==turn)finish(turn,reply.length()>0?reply+"\n[Interrupted] "+message:message,"error");});}
        });}catch(Exception e){finish(turn,e.getMessage(),"error");}
        return true;
    }
    private void speakSentences(boolean last){
        if(stoppingSpeech)return;
        String value=reply.toString();int end=spoken;
        for(int i=spoken;i<value.length();i++)if((value.charAt(i)=='.'||value.charAt(i)=='!'||value.charAt(i)=='?'||value.charAt(i)=='\n')&&(i+1==value.length()||Character.isWhitespace(value.charAt(i+1))))end=i+1;
        if(last)end=value.length();
        if(end>spoken&&(last||end-spoken>=18)){speaker.enqueue(value.substring(spoken,end));spoken=end;}
    }
    private void finish(JSONObject turn,String answer,String mode){
        put(turn,"reply",answer==null?"Sir, no reply was returned.":answer);put(turn,"mode",mode);save(turn);
        if(!stoppingSpeech){
            if(mode.equals("model")&&reply.length()>0){speakSentences(true);speaker.end();}
            else {speaker.say(turn.optString("reply"));}
        }else speaker.end();
        active=null;call=null;busy=false;phase=mode.equals("error")?"Connection needs attention":"Ready";changed();sync();
    }
    static void put(JSONObject object,String key,Object value){try{object.put(key,value);}catch(JSONException e){throw new IllegalStateException(e);}}
    void stop(){
        stoppingSpeech=true;speaker.stop();
        if(active==null){phase="Stopped speaking";changed();return;}
        JSONObject turn=active;String id=turn.optString("id");if(call!=null)call.cancel();
        active=null;call=null;busy=false;put(turn,"reply",turn.optString("reply")+" [Stopped]");put(turn,"mode","cancelled");save(turn);phase="Stopped";changed();
        network.execute(()->{try{BrainClient.request(prefs,"/v1/cancel",new JSONObject().put("request_id",id));}catch(Exception ignored){}sync();});
    }
    void sync(){
        if(!loaded||prefs.token().isEmpty()||!syncing.compareAndSet(false,true))return;
        disk.execute(()->{JSONArray pending=store.pending();if(pending.length()==0){syncing.set(false);return;}
            network.execute(()->{boolean success=false;try{JSONArray accepted=BrainClient.request(prefs,"/v1/events",new JSONObject().put("events",pending)).getJSONArray("accepted");disk.execute(()->store.synced(accepted));success=true;}catch(Exception ignored){}finally{syncing.set(false);if(success)disk.execute(this::sync);}});
        });
    }
}
