package com.kitty.ai;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Explicitly started microphone foreground service. No boot-start or hidden recording. */
public class VoiceService extends Service implements RecognitionListener {
    public static volatile VoiceService instance;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Model model;private Recognizer recognizer;private SpeechService speech;
    private TextToSpeech tts;private boolean ready,busy,destroyed,once;private long armedUntil;
    private String queuedSpeech;
    @Override public void onCreate(){
        super.onCreate();instance=this;
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("listening","KITTY microphone",NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel("actions","KITTY actions",NotificationManager.IMPORTANCE_DEFAULT));
        tts=new TextToSpeech(this,status->{
            ready=status==TextToSpeech.SUCCESS;
            if(ready){Speech.configure(tts,new Prefs(this));if(queuedSpeech!=null){String q=queuedSpeech;queuedSpeech=null;say(q);}}
        });
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
            public void onStart(String id){}
            public void onDone(String id){main.postDelayed(()->resume(),500);}
            public void onError(String id){main.post(()->resume());}
        });
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&"stop".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){stopSelf();return START_NOT_STICKY;}
        once=intent!=null&&intent.getBooleanExtra("once",false);
        startForeground(11,notification("Loading local speech model…"));
        if(speech!=null)return START_NOT_STICKY;
        worker.execute(()->{
            try {
                if(!ModelInstaller.installed(this))throw new Exception("Import the Vosk model in Settings first, Sir.");
                Model loaded=new Model(ModelInstaller.model(this).getAbsolutePath());
                main.post(()->{
                    if(destroyed){loaded.close();return;}
                    try {
                        model=loaded;recognizer=new Recognizer(model,16000.0f);speech=new SpeechService(recognizer,16000.0f);
                        speech.startListening(this);
                        if(once)main.postDelayed(()->{
                            if(!destroyed&&!busy){
                                emit("","Sir, I didn't hear a command. Tap to talk again.","","status");
                                stopSelf();
                            }
                        },10000);
                        update(once?"Listening for one command":"Listening • say Hey Kitty");
                        emit("","Sir, "+(once?"listening for your command.":"listening mode is on."),"","status");
                    }catch(Exception e){emit("","Sir, microphone setup failed. Close other recording apps and try again.","","error");stopSelf();}
                });
            }catch(Exception e){main.post(()->{emit("","Sir, local voice setup failed. Import a compatible small Vosk English model in Settings.","","error");stopSelf();});}
        });
        return START_NOT_STICKY;
    }
    private Notification notification(String text){
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,VoiceService.class).setAction("stop"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,"listening").setSmallIcon(R.drawable.ic_kitty).setContentTitle("KITTY AI").setContentText(text).setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"Stop listening",stop).build()).build();
    }
    private void update(String text){((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(11,notification(text));}
    public void arm(){armedUntil=SystemClock.elapsedRealtime()+10000;emit("","Sir, listening for your next command.","","status");}
    public void pauseForSpeech(){if(speech!=null){speech.setPause(true);speech.reset();}}
    public void resume(){
        if(destroyed)return;
        if(once&&busy){stopSelf();return;}
        busy=false;
        if(speech!=null){speech.reset();speech.setPause(false);}
        update("Listening • say Hey Kitty");
    }
    public void say(String text){
        if(destroyed)return;
        pauseForSpeech();
        if(!new Prefs(this).speak()){resume();return;}
        if(!ready){queuedSpeech=text;main.postDelayed(()->{if(!ready)resume();},2500);return;}
        String spoken=text.length()>3500?text.substring(0,3500):text;
        if(tts.speak(spoken,TextToSpeech.QUEUE_FLUSH,null,"kitty-"+System.nanoTime())==TextToSpeech.ERROR)resume();
    }
    private void emit(String user,String answer,String id,String mode){
        main.post(()->{
            MainActivity activity=MainActivity.active;
            if(activity!=null)activity.onVoiceEvent(user,answer,id,mode);
        });
    }
    private void heard(String json){
        if(destroyed||busy)return;
        try {
            String text=new JSONObject(json).optString("text","").trim();
            if(text.isEmpty())return;
            boolean wake=text.matches("(?i)^(?:(?:hey|hi|okay|ok)\\s+)?kitty(?:\\s.*)?$");
            if(!once&&!wake&&SystemClock.elapsedRealtime()>armedUntil)return;
            String command=wake?Router.stripWake(text):text;
            if(command.isEmpty()){armedUntil=SystemClock.elapsedRealtime()+10000;say("Yes, Sir?");return;}
            armedUntil=0;busy=true;pauseForSpeech();update("Working on your command…");
            Action action=Router.parse(command);
            if(action!=null){
                boolean screen=action.kind.equals("tap")||action.kind.equals("type")||action.kind.equals("scroll")||action.kind.equals("navigation")||action.kind.equals("battery");
                if(!screen&&KittyAccessibilityService.instance==null&&!MainActivity.visible){
                    String nonce=java.util.UUID.randomUUID().toString();
                    new Prefs(this).p.edit().putString("pending_action_nonce",nonce).putString("pending_action_command",command).commit();
                    Intent open=new Intent(this,MainActivity.class).putExtra("action_nonce",nonce).setAction("command-"+nonce);
                    PendingIntent pi=PendingIntent.getActivity(this,44,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                    ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(44,new Notification.Builder(this,"actions").setSmallIcon(R.drawable.ic_kitty).setContentTitle("KITTY • action ready").setContentText("Tap to continue your phone command").setContentIntent(pi).setAutoCancel(true).build());
                    String answer="Sir, tap the KITTY notification to open that app. Accessibility can enable background app actions.";
                    emit(command,answer,"","local");say(answer);return;
                }
                Context context=MainActivity.active!=null?MainActivity.active:this;
                Actions.execute(context,action,answer->{emit(command,answer,"","local");say(answer);});
                // A picker can defer its callback; allow a new wake command after a timeout.
                main.postDelayed(()->{if(busy&&!tts.isSpeaking())resume();},20000);
            }else{
                worker.execute(()->{
                    String answer,id="",mode="error";
                    try{JSONObject r=BrainClient.chat(new Prefs(this),command);answer=r.getString("reply");id=r.optString("response_id");mode=r.optString("mode");}
                    catch(Exception e){answer="Sir, I couldn't reach the laptop brain. Check that KITTY and the llama.cpp model server are running.";}
                    final String response=answer,responseId=id,responseMode=mode;
                    main.post(()->{if(!destroyed){emit(command,response,responseId,responseMode);say(response);}});
                });
            }
        }catch(Exception e){resume();}
    }
    @Override public void onResult(String hypothesis){main.post(()->heard(hypothesis));}
    @Override public void onFinalResult(String hypothesis){main.post(()->heard(hypothesis));}
    @Override public void onPartialResult(String hypothesis){}
    @Override public void onError(Exception exception){main.post(()->{emit("","Sir, microphone recognition stopped. Restart listening from KITTY.","","error");stopSelf();});}
    @Override public void onTimeout(){main.post(()->{emit("","Sir, I didn't hear a command.","","status");if(once)stopSelf();});}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){
        destroyed=true;if(instance==this)instance=null;main.removeCallbacksAndMessages(null);
        if(MainActivity.active!=null)MainActivity.active.onVoiceEvent("","","","status");
        if(speech!=null){speech.cancel();speech.shutdown();}if(recognizer!=null)recognizer.close();if(model!=null)model.close();
        if(tts!=null){tts.stop();tts.shutdown();}worker.shutdownNow();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
}
