package com.kitty.ai;
import android.content.Context;
import android.os.*;
import org.json.*;
import okhttp3.Call;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Process-owned state, serialized local writes and account-captured network operations. */
final class ChatController {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService disk=Executors.newSingleThreadExecutor(),network=Executors.newFixedThreadPool(2);
    private final ConversationStore store;private final Context app;final Prefs prefs;
    final List<JSONObject> turns=new ArrayList<>();private final Set<Runnable> observers=new HashSet<>();
    boolean busy,loaded,foreground;String phase="Ready";
    private SpeechOutput speaker;private JSONObject active;private BrainClient.Session activeAccount;private Call call;private int viewGeneration;
    private final AtomicBoolean syncing=new AtomicBoolean();
    private boolean redrawQueued;
    ChatController(Context context){app=context.getApplicationContext();prefs=new Prefs(app);store=new ConversationStore(app);disk.execute(store::recover);history();}
    void observe(Runnable r){observers.add(r);r.run();}void remove(Runnable r){observers.remove(r);}
    void changed(){for(Runnable r:new ArrayList<>(observers))r.run();}
    private void redraw(){if(redrawQueued)return;redrawQueued=true;main.postDelayed(()->{redrawQueued=false;changed();},60);}
    void note(String message){phase=message;changed();}
    private void history(){loaded=false;int own=++viewGeneration;BrainClient.Session account=new BrainClient.Session(prefs);String session=prefs.session();disk.execute(()->{List<JSONObject> old=account.uid.isEmpty()?new ArrayList<>():store.recent(session,account.pairing,true);main.post(()->{if(own!=viewGeneration||!account.uid.equals(prefs.accountId()))return;turns.clear();turns.addAll(old);loaded=true;changed();sync();});});}
    void accountChanged(){stop();turns.clear();phase="Ready";history();}
    void newChat(){if(busy)return;prefs.newSession();turns.clear();loaded=true;phase="Ready";viewGeneration++;changed();}
    void archive(Consumer<List<JSONObject>> result){
        BrainClient.Session account=new BrainClient.Session(prefs);note("Loading your history");
        network.execute(()->{JSONArray cloudRows=null;try{cloudRows=BrainClient.request(account,"/v1/history",null).getJSONArray("turns");}catch(Exception ignored){}
            final JSONArray restored=cloudRows;disk.execute(()->{if(restored!=null)store.importCloud(account.pairing,restored);List<JSONObject> rows=store.recent(null,account.pairing,true);
                main.post(()->{if(account.current(prefs)){note(restored==null?"Showing saved phone history · cloud unavailable":"Ready");result.accept(rows);}});
            });
        });
    }
    private void save(JSONObject turn){try{JSONObject copy=new JSONObject(turn.toString());disk.execute(()->store.save(copy));}catch(JSONException e){throw new IllegalStateException(e);}}
    boolean send(String value){
        String text=value.trim();if(!loaded||busy||text.isEmpty()||text.length()>8000){note(busy?"Finish or stop the current reply first.":"Use a message under 8,000 characters.");return false;}
        if(!prefs.signedIn()){note("Sign in with Google to continue.");return false;}
        stopAudio();JSONObject turn=new JSONObject();
        try{turn.put("id",UUID.randomUUID().toString()).put("session",prefs.session()).put("input",text).put("reply","").put("mode","pending").put("source","chat").put("created",System.currentTimeMillis()).put("pairing",store.pairing());}catch(JSONException e){return false;}
        turns.add(turn);active=turn;activeAccount=new BrainClient.Session(prefs);busy=true;phase="Connecting";save(turn);changed();
        BrainClient.Session account=activeAccount;
        // Core identity is always answered by the server. Only personal memory commands stay local.
        disk.execute(()->{String memory=store.memoryCommand(account.pairing,text);main.post(()->{if(active!=turn)return;if(memory!=null)finish(turn,memory,"memory");else remote(turn,text,account);});});return true;
    }
    private void remote(JSONObject turn,String text,BrainClient.Session account){
        network.execute(()->{try{
            if(!account.current(prefs))return;
            Call next=BrainClient.stream(account,text,turn.optString("session"),turn.optString("id"),new BrainClient.Events(){
                public void event(String kind,JSONObject data){main.post(()->{
                    if(active!=turn||!account.current(prefs))return;
                    if(kind.equals("status")){phase="Thinking";redraw();}
                    else if(kind.equals("token")){put(turn,"reply",turn.optString("reply")+data.optString("text"));phase="Replying";redraw();}
                    else if(kind.equals("done")){put(turn,"metadata",data.toString());finish(turn,data.optString("reply"),data.optString("mode","model"));}
                });}
                public void failed(String message){main.post(()->{if(active==turn)finish(turn,turn.optString("reply").isEmpty()?message:turn.optString("reply")+"\n\n"+message,"error");});}
            });
            main.post(()->{if(active==turn&&account.current(prefs))call=next;else next.cancel();});
        }catch(Exception e){main.post(()->{if(active==turn)finish(turn,e.getMessage()==null?"Could not connect to KITTY.":e.getMessage(),"error");});}});
    }
    private void finish(JSONObject turn,String answer,String mode){
        put(turn,"reply",answer==null||answer.isEmpty()?"No reply was returned. Please retry.":answer);put(turn,"mode",mode);save(turn);
        active=null;activeAccount=null;call=null;busy=false;phase=mode.equals("error")||mode.equals("unavailable")?"Please retry":"Ready";changed();
        if(foreground&&prefs.speak()&&!mode.equals("error")&&!mode.equals("unavailable"))read(turn.optString("reply"));sync();
    }
    static void put(JSONObject object,String key,Object value){try{object.put(key,value);}catch(JSONException e){throw new IllegalStateException(e);}}
    void read(String text){if(!foreground)return;if(speaker==null)speaker=new SpeechOutput(app);speaker.beginCloud();speaker.enqueue(text);speaker.end();}
    void stopAudio(){if(speaker!=null)speaker.stop();}
    void stop(){
        stopAudio();if(active==null)return;
        JSONObject turn=active;BrainClient.Session account=activeAccount;String id=turn.optString("id");if(call!=null)call.cancel();
        active=null;activeAccount=null;call=null;busy=false;put(turn,"reply",turn.optString("reply")+(turn.optString("reply").isEmpty()?"Stopped.":"\n\n[Stopped]"));put(turn,"mode","cancelled");save(turn);phase="Stopped";changed();
        network.execute(()->{try{BrainClient.request(account,"/v1/cancel",new JSONObject().put("request_id",id));}catch(Exception ignored){}});
    }
    void feedback(String id,int rating,String correction){disk.execute(()->{store.feedback(id,rating,correction);sync();});}
    void sync(){
        if(!loaded||!prefs.signedIn()||!syncing.compareAndSet(false,true))return;
        BrainClient.Session account=new BrainClient.Session(prefs);
        disk.execute(()->{JSONArray pending=store.pending(account.pairing),memories=store.memoryPending(account.pairing);List<JSONObject> feedback=store.feedbackPending(account.pairing);
            network.execute(()->{boolean success=false;try{
                if(!account.current(prefs))return;
                JSONArray rows=BrainClient.request(account,"/v1/memories",new JSONObject().put("changes",memories)).getJSONArray("memories");disk.execute(()->store.memoryMerged(account.pairing,memories,rows));
                if(pending.length()>0){JSONArray accepted=BrainClient.request(account,"/v1/events",new JSONObject().put("events",pending)).getJSONArray("accepted");disk.execute(()->store.synced(accepted));}
                for(JSONObject f:feedback){BrainClient.request(account,"/v1/feedback",f);disk.execute(()->store.feedbackSynced(f));}success=true;
            }catch(Exception ignored){}finally{syncing.set(false);boolean drain=success&&(pending.length()==20||memories.length()>0);main.post(()->{if(drain||!account.current(prefs))sync();});}});
        });
    }
}
