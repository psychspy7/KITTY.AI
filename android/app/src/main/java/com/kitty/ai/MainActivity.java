package com.kitty.ai;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.JSONObject;
import java.util.*;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int BG=0xFF0C0D12,CARD=0xFF191A23,INK=0xFFF4F0FF,MUTED=0xFFAAA6B9,ACCENT=0xFFC5B6FF;
    private static final String UPDATE_MANIFEST="https://raw.githubusercontent.com/psychspy7/KITTY.AI/main/release/update.json";
    private final ExecutorService worker=Executors.newFixedThreadPool(2);
    private final Handler main=new Handler(Looper.getMainLooper());
    private LinearLayout chat;private ScrollView scroll;private EditText input;private TextView status,heard;private OrbView orb;
    private Prefs prefs;private ChatController controller;private Button listen;private VoiceService voice;private boolean bound,visible;
    private final Map<String,TextView> replies=new HashMap<>();private final Set<String> completed=new HashSet<>();
    private final Runnable changed=this::render;
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder binder){voice=((VoiceService.LocalBinder)binder).service();}
        public void onServiceDisconnected(ComponentName name){voice=null;}
    };
    private void bindVoice(){if(visible&&controller.voiceRunning&&!bound)bound=bindService(new Intent(this,VoiceService.class),connection,0);}
    private void unbindVoice(){if(bound){unbindService(connection);bound=false;voice=null;}}
    private String appVersionName(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){return "unknown";}}
    private int appVersionCode(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionCode;}catch(Exception e){return 0;}}
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    private Button button(String title,Runnable action){Button b=new Button(this);b.setText(title);b.setTextSize(12);b.setAllCaps(false);b.setTextColor(INK);b.setBackground(bg(CARD,12));b.setMinHeight(dp(42));b.setPadding(dp(12),dp(7),dp(12),dp(7));b.setOnClickListener(v->action.run());return b;}
    private void addButton(LinearLayout row,Button b){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);lp.setMargins(dp(3),0,dp(3),0);row.addView(b,lp);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);prefs=new Prefs(this);controller=KittyApp.chat(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        root.setPadding(dp(20),dp(12),dp(20),dp(12));
        root.setOnApplyWindowInsetsListener((v,insets)->{
            int top=insets.getSystemWindowInsetTop(),bottom=insets.getSystemWindowInsetBottom();
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());top=b.top;bottom=b.bottom;}
            v.setPadding(dp(20),top+dp(12),dp(20),bottom+dp(12));return insets;
        });
        setContentView(root);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);
        TextView brand=text("KITTY AI",20,INK);brand.setLetterSpacing(.14f);brand.setTypeface(null,Typeface.BOLD);titles.addView(brand);
        status=text("PERSONAL SYSTEM  /  "+appVersionName(),10,MUTED);status.setPadding(0,dp(5),0,0);titles.addView(status);
        header.addView(titles,new LinearLayout.LayoutParams(0,dp(58),1));
        header.addView(button("Update",this::checkForUpdate),new LinearLayout.LayoutParams(dp(72),dp(42)));
        header.addView(button("Settings",this::settings),new LinearLayout.LayoutParams(dp(85),dp(42)));root.addView(header);
        orb=new OrbView(this);root.addView(orb,new LinearLayout.LayoutParams(-1,dp(90)));
        TextView greeting=text("At your service, Sir.",24,INK);greeting.setGravity(Gravity.CENTER);greeting.setTypeface(null,Typeface.BOLD);root.addView(greeting);
        TextView subtitle=text("A little wit. A mind of your own.",12,MUTED);subtitle.setGravity(Gravity.CENTER);subtitle.setPadding(0,dp(7),0,dp(15));root.addView(subtitle);
        heard=text("",11,ACCENT);heard.setMaxLines(2);root.addView(heard);
        LinearLayout chips=new LinearLayout(this);
        addButton(chips,button("YouTube",()->send("open YouTube")));
        addButton(chips,button("Weather",()->{input.setText("weather in ");input.setSelection(input.length());input.requestFocus();}));
        addButton(chips,button("Memory",()->send("show memories")));
        addButton(chips,button("Stop",()->controller.stop()));root.addView(chips);
        scroll=new ScrollView(this);scroll.setFillViewport(true);
        chat=new LinearLayout(this);chat.setOrientation(LinearLayout.VERTICAL);chat.setPadding(0,dp(14),0,dp(6));scroll.addView(chat);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout compose=new LinearLayout(this);compose.setGravity(Gravity.CENTER_VERTICAL);
        input=new EditText(this);input.setTextColor(INK);input.setTextSize(15);input.setHintTextColor(MUTED);input.setHint("Talk to KITTY…");input.setMaxLines(3);input.setSingleLine(false);input.setBackground(bg(CARD,14));input.setPadding(dp(14),dp(10),dp(10),dp(10));
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);input.setOnEditorActionListener((v,id,event)->{if(id==android.view.inputmethod.EditorInfo.IME_ACTION_SEND){sendInput();return true;}return false;});
        compose.addView(input,new LinearLayout.LayoutParams(0,dp(52),1));
        Button send=button("Send",this::sendInput);send.setTextColor(BG);send.setBackground(bg(ACCENT,14));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(66),dp(52));sp.setMargins(dp(8),0,0,0);compose.addView(send,sp);root.addView(compose);
        LinearLayout bottom=new LinearLayout(this);bottom.setPadding(0,dp(10),0,0);
        addButton(bottom,button("Tap to talk",this::microphone));
        listen=button("Hey Kitty: off",this::toggleListening);addButton(bottom,listen);
        addButton(bottom,button("New chat",()->controller.newChat()));root.addView(bottom);
        bubble("Sir, I'm KITTY. Open Settings to pair my laptop brain. App commands can already work on this phone. Try ‘open YouTube’ or ‘battery’.",false,"","status");
    }
    private void render(){
        if(isDestroyed()||!visible)return;
        status.setText(!controller.busy&&prefs.speak()&&!controller.speaker.issue.isEmpty()?controller.speaker.issue:controller.phase);listen.setText(controller.voiceRunning?"Hey Kitty: on":"Hey Kitty: off");
        heard.setText(controller.voiceRunning?controller.voiceState+(controller.heard.isEmpty()?"":"\nHeard: "+controller.heard):"");
        orb.active(controller.busy||controller.speaker.active());
        Set<String> ids=new HashSet<>();for(JSONObject t:controller.turns)ids.add(t.optString("id"));
        if(!ids.containsAll(replies.keySet())){chat.removeAllViews();replies.clear();completed.clear();}
        for(JSONObject t:controller.turns){
            String id=t.optString("id");TextView body=replies.get(id);
            if(body==null){bubble(t.optString("input"),true,"","");body=bubble(t.optString("reply"),false,"",t.optString("mode"));replies.put(id,body);}
            String answer=t.optString("reply");if(answer.isEmpty())answer="Waiting for KITTY…";
            if(!body.getText().toString().equals(answer))body.setText(answer);
            if("model".equals(t.optString("mode"))&&completed.add(id))addFeedback((LinearLayout)body.getParent(),id);
        }
        if(controller.voiceRunning)bindVoice();else unbindVoice();
    }
    @Override protected void onStart(){super.onStart();visible=true;controller.foreground=new WeakReference<>(this);controller.observe(changed);controller.sync();bindVoice();}
    @Override protected void onStop(){visible=false;controller.remove(changed);if(controller.foreground.get()==this)controller.foreground.clear();unbindVoice();super.onStop();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);}
    private TextView bubble(String message,boolean user,String id,String mode){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setBackground(bg(user?0xFF29243B:CARD,14));box.setPadding(dp(14),dp(11),dp(14),dp(11));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(user?dp(30):0,0,user?0:dp(18),dp(9));chat.addView(box,lp);
        TextView label=text(user?"YOU":"KITTY"+(mode!=null&&mode.equals("local")?"  ·  PHONE":""),9,user?MUTED:ACCENT);label.setLetterSpacing(.14f);box.addView(label);
        TextView body=text(message,14,INK);body.setTextIsSelectable(true);body.setPadding(0,dp(5),0,0);body.setLineSpacing(dp(2),1.06f);box.addView(body);
        if(!user&&"model".equals(mode)&&id!=null&&!id.isEmpty())addFeedback(box,id);
        scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));return body;
    }
    private void addFeedback(LinearLayout box,String id){
        LinearLayout feedback=new LinearLayout(this);feedback.setPadding(0,dp(5),0,0);
        addButton(feedback,button("Good reply",()->feedback(id,1,"")));
        addButton(feedback,button("Correct it",()->{EditText correction=new EditText(this);correction.setHint("What should KITTY have said?");new AlertDialog.Builder(this).setTitle("Teach KITTY, Sir").setView(correction).setPositiveButton("Save correction",(d,w)->feedback(id,-1,correction.getText().toString())).setNegativeButton("Cancel",null).show();}));box.addView(feedback);
    }
    private void feedback(String id,int rating,String correction){
        if(correction.length()>8000){Toast.makeText(this,"Keep the correction under 8,000 characters, Sir.",Toast.LENGTH_LONG).show();return;}
        controller.feedback(id,rating,correction,()->Toast.makeText(getApplicationContext(),"Saved on this phone. Feedback syncs when the laptop connects; model weights haven't changed.",Toast.LENGTH_LONG).show());
    }
    private void sendInput(){String value=input.getText().toString().trim();if(controller.send(this,value,"typed"))input.setText("");}
    private void send(String value){controller.send(this,value,"typed");}
    private void microphone(){if(controller.busy)controller.stop();if(voice!=null){voice.arm();return;}startVoice(true);}
    private void requestVoiceStop(){
        boolean requested=controller.voiceRunning;unbindVoice();controller.voice("Off",null,false);
        if(!requested)return;
        try{startService(new Intent(this,VoiceService.class).setAction("stop"));}
        catch(RuntimeException e){controller.note("Android could not stop the microphone cleanly. Open app settings and stop KITTY, Sir.");}
    }
    private void toggleListening(){if(controller.voiceRunning)requestVoiceStop();else startVoice(false);}
    private void startVoice(boolean once){
        if(!ModelInstaller.installed(this)){bubble("Sir, import a small Vosk English model in Settings first. Listening runs locally on your phone.",false,"","status");return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},once?21:22);return;}
        controller.speaker.stop();
        try{
            startForegroundService(new Intent(this,VoiceService.class).putExtra("once",once));
            // Acknowledge the request before another tap can enqueue a second start.
            controller.voice("Starting microphone", "", true);main.postDelayed(this::bindVoice,200);
        }
        catch(RuntimeException e){controller.voice("Off",null,false);controller.note("Android blocked microphone startup. Keep KITTY open and retry, Sir.");}
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED){if(code==21)startVoice(true);else if(code==22)startVoice(false);}}
    private EditText field(LinearLayout box,String label,String value,boolean secret){
        TextView title=text(label,12,MUTED);title.setPadding(0,dp(10),0,0);box.addView(title);EditText edit=new EditText(this);edit.setText(value);edit.setSingleLine(true);edit.setTextSize(14);edit.setInputType(secret?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);box.addView(edit);return edit;
    }
    private void settings(){
        ScrollView container=new ScrollView(this);LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(20),dp(6),dp(20),dp(10));container.addView(form);
        form.addView(text("Pair my brain. Choose my voice. Give me the access I need, Sir.",14,INK));
        EditText url=field(form,"Laptop server URL",prefs.url(),false);
        EditText token=field(form,"Pairing token",prefs.token(),true);
        EditText country=field(form,"Country calling code",prefs.country(),false);
        CheckBox spoken=new CheckBox(this);spoken.setText("Speak replies");spoken.setChecked(prefs.speak());form.addView(spoken);
        CheckBox direct=new CheckBox(this);direct.setText("Direct calls after a clear command");direct.setChecked(prefs.directCalls());form.addView(direct);
        form.addView(text("With direct calls off, KITTY opens the dialer. Internet access is already enabled. Use USB loopback or trusted HTTPS for the laptop connection.",11,MUTED));
        form.addView(button("Check saved connection",()->worker.execute(()->{
            try{JSONObject s=BrainClient.request(prefs,"/v1/status",null);String result=s.optBoolean("model_ready")?"Sir, connected. Model: "+s.optString("model"):"Sir, KITTY connected; start the llama.cpp model server with alias "+s.optString("model")+".";main.post(()->{if(!isFinishing()&&!isDestroyed())new AlertDialog.Builder(this).setMessage(result).setPositiveButton("OK",null).show();});}
            catch(Exception e){main.post(()->{if(!isFinishing()&&!isDestroyed())new AlertDialog.Builder(this).setMessage("Sir, connection failed. Save your settings first, then check the laptop server and token.").setPositiveButton("OK",null).show();});}
        })));
        form.addView(button("Check for app updates",this::checkForUpdate));
        form.addView(text("KITTY checks the signed-release manifest over HTTPS. Android will ask you to confirm the download and installation; your chats and pairing settings stay on the phone.",11,MUTED));
        form.addView(button("Grant microphone, contacts and call access",()->{
            ArrayList<String> wanted=new ArrayList<>(Arrays.asList(Manifest.permission.RECORD_AUDIO,Manifest.permission.READ_CONTACTS,Manifest.permission.CALL_PHONE));if(Build.VERSION.SDK_INT>=33)wanted.add(Manifest.permission.POST_NOTIFICATIONS);
            wanted.removeIf(p->checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED);if(!wanted.isEmpty())requestPermissions(wanted.toArray(new String[0]),24);else Toast.makeText(this,"These permissions are already granted, Sir.",Toast.LENGTH_LONG).show();
        }));
        form.addView(button("Enable screen control (Accessibility)",()->new AlertDialog.Builder(this).setTitle("Screen control").setMessage("KITTY can read the active screen locally and tap, type, scroll or navigate when you tell her to. Screen contents are not uploaded. Android will ask you to enable KITTY AI on the next screen.").setPositiveButton("Open Settings",(d,w)->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).setNegativeButton("Cancel",null).show()));
        form.addView(button("Connect Shizuku (optional)",()->((KittyApp)getApplication()).shizuku.enable()));
        form.addView(text("Shizuku supports Home, Back, Recents, Lock and tap X Y. Start it in the Shizuku app first. Label taps, scrolling and typing use Accessibility.",11,MUTED));
        form.addView(button("Choose an installed voice",this::chooseVoice));
        form.addView(button("Show recent archived chats",()->controller.history(true)));
        form.addView(button("Battery optimization settings",()->startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))));
        form.addView(button("Download offline English speech model",()->new AlertDialog.Builder(this).setTitle("Try the model that hears you best").setItems(new String[]{"Indian English · 36 MB","US English · 40 MB"},(d,i)->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://alphacephei.com/vosk/models/"+(i==0?"vosk-model-small-en-in-0.4.zip":"vosk-model-small-en-us-0.15.zip"))))).show()));
        form.addView(button(ModelInstaller.installed(this)?"Replace offline speech model":"Import offline speech model ZIP",()->{requestVoiceStop();Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.addCategory(Intent.CATEGORY_OPENABLE);pick.setType("*/*");startActivityForResult(pick,70);}));
        form.addView(button("App permissions & battery settings",()->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))));
        form.addView(text("Voice: use English commands with these models. Hindi speech packs do not enable Hindi wake words or commands yet. General chat can use Hindi/Hinglish through the laptop model. A female speaking voice depends on the voices installed on this phone. No root or device-owner enrollment is performed.",11,MUTED));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("KITTY Settings").setView(container).setPositiveButton("Save",null).setNegativeButton("Close",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try {
                String server=url.getText().toString().trim(),cc=country.getText().toString().replace("+","").trim();BrainClient.validateUrl(server);if(!cc.matches("[1-9][0-9]{0,3}"))throw new Exception("Use a valid country calling code.");
                prefs.token(token.getText().toString().trim());prefs.p.edit().putString("url",server).putString("country",cc).putBoolean("speak",spoken.isChecked()).putBoolean("direct_calls",direct.isChecked()).apply();
                if(direct.isChecked()&&checkSelfPermission(Manifest.permission.CALL_PHONE)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.CALL_PHONE},25);
                controller.sync();if(!prefs.speak())controller.speaker.stop();dialog.dismiss();Toast.makeText(this,"Settings saved, Sir.",Toast.LENGTH_SHORT).show();
            }catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
        }));dialog.show();
    }
    private void checkForUpdate(){
        Toast.makeText(this,"Checking for a KITTY update…",Toast.LENGTH_SHORT).show();
        worker.execute(()->{
            try{
                AppUpdater.UpdateInfo info=AppUpdater.fetch(UPDATE_MANIFEST);
                main.post(()->showUpdateResult(info));
            }catch(Exception e){
                main.post(()->{if(!isFinishing()&&!isDestroyed())new AlertDialog.Builder(this).setTitle("Update check failed").setMessage("Sir, I couldn't reach the update server. Check your internet connection and try again.\n\n"+e.getMessage()).setPositiveButton("OK",null).show();});
            }
        });
    }
    private void showUpdateResult(AppUpdater.UpdateInfo info){
        if(isFinishing()||isDestroyed())return;
        if(!info.isNewerThan(appVersionCode())){
            new AlertDialog.Builder(this).setTitle("KITTY is up to date").setMessage("You are running KITTY "+appVersionName()+" (build "+appVersionCode()+").").setPositiveButton("OK",null).show();
            return;
        }
        StringBuilder message=new StringBuilder("KITTY ").append(info.versionName.isEmpty()?info.latestVersionCode:info.versionName).append(" is ready.\n\n");
        if(!info.releaseNotes.isEmpty())message.append(info.releaseNotes).append("\n\n");
        message.append("Android will open the download page so you can review and confirm the install.");
        new AlertDialog.Builder(this).setTitle("Update available").setMessage(message.toString()).setNegativeButton("Later",null).setPositiveButton("Download",(d,w)->{
            try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(info.downloadUrl)));}
            catch(Exception e){Toast.makeText(this,"No browser can open the update link, Sir.",Toast.LENGTH_LONG).show();}
        }).show();
    }
    private void chooseVoice(){
        List<Voice> voices=controller.speaker.voices();
        if(voices.isEmpty()){Toast.makeText(this,"Download an offline English or Hindi TTS voice in Android settings first, Sir.",Toast.LENGTH_LONG).show();return;}
        new AlertDialog.Builder(this).setTitle("Choose and preview a voice").setItems(voices.stream().map(Voice::getName).toArray(String[]::new),(d,i)->controller.speaker.preview(voices.get(i))).show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==70&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();bubble("Sir, importing the offline model. This may take a moment.",false,"","status");worker.execute(()->{
                String message;try{ModelInstaller.install(this,uri);message="Sir, offline speech is ready. Tap Hey Kitty to start listening.";}catch(Exception e){message="Sir, model import failed: "+e.getMessage();}
                final String finalMessage=message;main.post(()->{controller.note(finalMessage);if(!isDestroyed())bubble(finalMessage,false,"","status");});
            });
        }
    }
    @Override public void onDestroy(){worker.shutdown();main.removeCallbacksAndMessages(null);super.onDestroy();}
}
