package com.kitty.ai;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.media.*;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import java.util.concurrent.*;

/** Visible, explicitly started microphone owner. Commands use a local binder. */
public class VoiceService extends Service implements RecognitionListener {
    public final class LocalBinder extends Binder { VoiceService service(){return VoiceService.this;} }
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService loader=Executors.newSingleThreadExecutor();
    private final WakeGate gate=new WakeGate();
    private final Runnable chatChanged=this::audioState;
    private ChatController chat;private Model model;private Recognizer recognizer;private LocalSpeechEngine speech;
    private AudioManager audio;private AudioFocusRequest listeningFocus;private boolean ducked,silenced;
    private final Runnable releaseFocus=()->{if(audio!=null&&ducked){audio.abandonAudioFocusRequest(listeningFocus);ducked=false;}};
    private boolean stopping,starting,destroyed,once,submitted,paused,pendingArm,foreground;
    private long partialAt;private String shown="";
    @Override public void onCreate(){
        super.onCreate();chat=KittyApp.chat(this);
        audio=getSystemService(AudioManager.class);
        listeningFocus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).setOnAudioFocusChangeListener(change->{},main).build();
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("listening","KITTY microphone",NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel("actions","KITTY phone actions",NotificationManager.IMPORTANCE_DEFAULT));
        chat.observe(chatChanged);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        // A startForegroundService request must always be acknowledged before any
        // early stop or permission branch. Otherwise a very fast start/stop can
        // make Android kill the app with ForegroundServiceDidNotStartInTimeException.
        if(!foreground){
            try{startForeground(11,notification("Loading local speech model…"));foreground=true;}
            catch(RuntimeException e){chat.note("Android could not start the microphone. Open KITTY and try again, Sir.");stopListening();return START_NOT_STICKY;}
        }
        if(intent!=null&&"stop".equals(intent.getAction())){stopListening();return START_NOT_STICKY;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){chat.note("Grant microphone access in Settings, Sir.");stopListening();return START_NOT_STICKY;}
        boolean tap=intent!=null&&(intent.getBooleanExtra("once",false)||"listen".equals(intent.getAction()));
        if(starting||speech!=null){if(tap)arm();return START_NOT_STICKY;}
        once=tap;pendingArm=tap;starting=true;
        state("Loading speech model",null);
        loader.execute(()->{
            Model m=null;Recognizer r=null;LocalSpeechEngine s=null;
            try{
                if(!ModelInstaller.installed(this))throw new Exception("Missing model");
                m=new Model(ModelInstaller.model(this).getAbsolutePath());r=new Recognizer(m,16000f);s=new LocalSpeechEngine(r,this::meter);
                final Model loaded=m;final Recognizer rec=r;final LocalSpeechEngine service=s;
                main.post(()->{
                    if(destroyed||stopping){close(service,rec,loaded);return;}
                    starting=false;model=loaded;recognizer=rec;speech=service;
                    if(!speech.startListening(this)){chat.note("Microphone could not start. Close other recording apps and retry, Sir.");stopListening();return;}
                    audioState();
                    if(once)arm();else state("Listening · say Hey Kitty",null);
                });
            }catch(Exception e){close(s,r,m);main.post(()->{if(!destroyed){chat.note("Offline speech setup failed. Import a compatible small Vosk English model, Sir.");stopListening();}});}
        });return START_NOT_STICKY;
    }
    private void stopListening(){
        if(stopping)return;stopping=true;if(speech!=null)speech.setPause(true);
        chat.voice("Off",null,false);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
    }
    private static void close(LocalSpeechEngine s,Recognizer r,Model m){if(s!=null)s.shutdown();if(r!=null)r.close();if(m!=null)m.close();}
    private void meter(float level,boolean blocked){
        if(destroyed||stopping)return;chat.micLevel=level;
        if(blocked!=silenced){silenced=blocked;if(blocked)state("Microphone blocked · close other recording apps",null);else audioState();}
        chat.changed();
    }
    private void duck(){
        main.removeCallbacks(releaseFocus);
        if(!ducked)ducked=audio.requestAudioFocus(listeningFocus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        main.postDelayed(releaseFocus,10000);
    }
    private Notification notification(String text){
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,VoiceService.class).setAction("stop"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent listen=PendingIntent.getService(this,2,new Intent(this,VoiceService.class).setAction("listen"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,"listening").setSmallIcon(R.drawable.ic_kitty).setContentTitle("KITTY AI · microphone on").setContentText(text).setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"Listen now",listen).build()).addAction(new Notification.Action.Builder(null,"Stop listening",stop).build()).build();
    }
    private void state(String text,String words){
        if(destroyed||stopping)return;
        boolean changed=!text.equals(shown);shown=text;
        if(changed||words!=null)chat.voice(text,words,true);
        if(changed&&foreground&&getSystemService(NotificationManager.class).areNotificationsEnabled())getSystemService(NotificationManager.class).notify(11,notification(text));
    }
    public void arm(){if(destroyed||stopping)return;pendingArm=true;submitted=false;chat.stop();duck();audioState();}
    private void audioState(){
        if(destroyed||stopping||speech==null)return;
        if(silenced){state("Microphone blocked · close other recording apps",null);return;}
        boolean hold=chat.busy||chat.speaker.active();
        if(hold!=paused){paused=hold;speech.setPause(hold);speech.reset();}
        if(hold){state(chat.speaker.active()?"Speaking · microphone paused":"Thinking · microphone paused",null);return;}
        if(once&&submitted){stopListening();return;}
        if(pendingArm){pendingArm=false;gate.arm(SystemClock.elapsedRealtime());main.postDelayed(()->{
            if(!destroyed&&!chat.busy&&!chat.speaker.active()&&!gate.armed(SystemClock.elapsedRealtime())){if(once){chat.note("I didn't hear a command. Tap to talk again, Sir.");stopListening();}else state("Listening · say Hey Kitty",null);}
        },10500);}
        state(gate.armed(SystemClock.elapsedRealtime())?"Listening · say your command":"Listening · say Hey Kitty",null);
    }
    private void heard(String json){
        if(destroyed||paused||chat.busy)return;
        try{
            String text=new JSONObject(json).optString("text").trim();if(text.isEmpty())return;
            state(shown,text);
            String command=gate.accept(text,SystemClock.elapsedRealtime());if(command==null)return;
            if(command.isEmpty()){pendingArm=true;chat.speaker.say("Yes, Sir?");audioState();return;}
            submitted=true;
            releaseFocus.run();
            Action action=Router.parse(command);
            Activity activity=chat.foreground.get();
            boolean screen=action!=null&&(action.kind.equals("select_result")||action.kind.equals("tap")||action.kind.equals("type")||action.kind.equals("scroll")||action.kind.equals("navigation")||action.kind.equals("battery"));
            if(action!=null&&!screen&&activity==null&&KittyAccessibilityService.instance==null){
                if(getSystemService(NotificationManager.class).areNotificationsEnabled()){
                    Intent i=new Intent(this,ActionActivity.class).putExtra("command",command).setAction("command-"+System.nanoTime());
                    PendingIntent pi=PendingIntent.getActivity(this,44,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                    getSystemService(NotificationManager.class).notify(44,new Notification.Builder(this,"actions").setSmallIcon(R.drawable.ic_kitty).setContentTitle("KITTY · action ready").setContentText("Tap to continue your phone command").setContentIntent(pi).setAutoCancel(true).build());
                    chat.note("Tap the KITTY notification to continue, Sir.");chat.speaker.say("Sir, tap my notification to continue.");
                }else {chat.note("Open KITTY to run that command, or allow notifications in Settings, Sir.");chat.speaker.say("Sir, open KITTY to run that command.");}
                audioState();return;
            }
            chat.send(activity!=null?activity:this,command,"voice");audioState();
        }catch(Exception e){chat.note("I couldn't read that speech result. Please retry, Sir.");audioState();}
    }
    @Override public void onResult(String text){main.post(()->heard(text));}
    @Override public void onFinalResult(String text){main.post(()->heard(text));}
    @Override public void onPartialResult(String json){main.post(()->{if(destroyed||paused||SystemClock.elapsedRealtime()-partialAt<400)return;try{String words=new JSONObject(json).optString("partial");if(!words.isEmpty()){partialAt=SystemClock.elapsedRealtime();if(Router.hasWakePhrase(words))duck();state(shown,words);}}catch(Exception ignored){}});}
    @Override public void onError(Exception e){main.post(()->{if(!destroyed){chat.note("Speech recognition stopped. Restart Hey Kitty, Sir.");stopListening();}});}
    @Override public void onTimeout(){main.post(()->{if(!destroyed){if(once)stopListening();else audioState();}});}
    @Override public IBinder onBind(Intent intent){return new LocalBinder();}
    @Override public void onDestroy(){
        destroyed=true;chat.remove(chatChanged); // Keep load-completion callbacks: they close native resources after destruction.
        main.removeCallbacks(releaseFocus);releaseFocus.run();chat.micLevel=0;
        LocalSpeechEngine s=speech;Recognizer r=recognizer;Model m=model;speech=null;recognizer=null;model=null;
        loader.execute(()->close(s,r,m));loader.shutdown();
        chat.voice("Off",null,false);stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
}
