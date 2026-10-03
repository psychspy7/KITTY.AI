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
    private static final int BG=0xFF0A1113,CARD=0xFF152126,INK=0xFFF3F5EF,MUTED=0xFF9EB1B2,ACCENT=0xFFB7E6C8;
    private static final String UPDATE_MANIFEST="https://raw.githubusercontent.com/psychspy7/KITTY.AI/main/release/update.json";
    private final ExecutorService worker=Executors.newFixedThreadPool(2);
    private final Handler main=new Handler(Looper.getMainLooper());
    private LinearLayout chat;private ScrollView scroll;private EditText input;private TextView status,heard;private OrbView orb;
    private TextView connectionStatus;private ProgressBar micMeter;private JSONObject diagnostics;private long checkedAt;
    private java.io.File pendingApk;private boolean updating;
    private Prefs prefs;private ChatController controller;private Button listen;private VoiceService voice;private boolean bound,visible;
    private CloudUi cloudUi;
    private final Runnable pollNotices=new Runnable(){public void run(){if(!visible)return;if(prefs.cloud()&&!prefs.accountId().isEmpty())worker.execute(()->{try{NoticeWorker.fetch(getApplicationContext());}catch(Exception ignored){}});main.postDelayed(this,60000);}};
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
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));d.setStroke(dp(1),0xFF26373A);return d;}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    private Button button(String title,Runnable action){Button b=new Button(this);b.setText(title);b.setTextSize(12);b.setAllCaps(false);b.setTextColor(INK);b.setBackground(bg(CARD,12));b.setMinHeight(dp(42));b.setPadding(dp(12),dp(7),dp(12),dp(7));b.setOnClickListener(v->action.run());return b;}
    private void addButton(LinearLayout row,Button b){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);lp.setMargins(dp(3),0,dp(3),0);row.addView(b,lp);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);prefs=new Prefs(this);controller=KittyApp.chat(this);cloudUi=new CloudUi(this,this::checkForUpdate,this::offlineSetup);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        root.setPadding(dp(20),dp(12),dp(20),dp(12));
        root.setOnApplyWindowInsetsListener((v,insets)->{
            int top=insets.getSystemWindowInsetTop(),bottom=insets.getSystemWindowInsetBottom();
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());top=b.top;bottom=b.bottom;}
            v.setPadding(dp(20),top+dp(12),dp(20),bottom+dp(12));return insets;
        });
        setContentView(root);
        root.setAlpha(0f);root.animate().alpha(1f).setDuration(280).start();
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);
        TextView brand=text("KITTY AI",23,INK);brand.setLetterSpacing(.09f);brand.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));titles.addView(brand);
        status=text("PERSONAL SYSTEM  /  "+appVersionName(),10,MUTED);status.setPadding(0,dp(5),0,0);titles.addView(status);
        header.addView(titles,new LinearLayout.LayoutParams(0,dp(58),1));
        header.addView(button("Update",this::checkForUpdate),new LinearLayout.LayoutParams(dp(72),dp(42)));
        header.addView(button("Settings",this::settings),new LinearLayout.LayoutParams(dp(85),dp(42)));root.addView(header);
        LinearLayout hero=new LinearLayout(this);hero.setGravity(Gravity.CENTER_VERTICAL);hero.setPadding(dp(18),dp(10),dp(4),dp(10));
        GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF213930,0xFF101D24});gradient.setCornerRadius(dp(24));gradient.setStroke(dp(1),0xFF345247);hero.setBackground(gradient);
        LinearLayout intro=new LinearLayout(this);intro.setOrientation(LinearLayout.VERTICAL);
        TextView edition=text("PERSONAL INTELLIGENCE  /  04",9,ACCENT);edition.setLetterSpacing(.12f);intro.addView(edition);
        TextView greeting=text("Your mind,\namplified.",28,INK);greeting.setTypeface(Typeface.create("serif",Typeface.NORMAL));greeting.setPadding(0,dp(7),0,dp(5));intro.addView(greeting);
        intro.addView(text("At your service, Sir. Created by Virat.",11,MUTED));hero.addView(intro,new LinearLayout.LayoutParams(0,-2,1));
        orb=new OrbView(this);hero.addView(orb,new LinearLayout.LayoutParams(dp(105),dp(124)));root.addView(hero,new LinearLayout.LayoutParams(-1,dp(148)));hero.setTranslationY(dp(8));hero.animate().translationY(0).setDuration(380).start();
        connectionStatus=text("○  Brain not checked   ·   SYSTEM STATUS  ↗",11,ACCENT);connectionStatus.setPadding(dp(4),dp(13),0,dp(13));connectionStatus.setMinHeight(dp(46));connectionStatus.setOnClickListener(v->refreshConnection(true));root.addView(connectionStatus);
        heard=text("",11,ACCENT);heard.setMaxLines(2);root.addView(heard);
        micMeter=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);micMeter.setMax(100);micMeter.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));micMeter.setVisibility(View.GONE);root.addView(micMeter,new LinearLayout.LayoutParams(-1,dp(4)));
        LinearLayout chips=new LinearLayout(this);
        addButton(chips,button("YouTube",()->send("open YouTube")));
        addButton(chips,button("Web",()->{input.setText("research ");input.setSelection(input.length());input.requestFocus();}));
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
        bubble(prefs.cloud()?"Sir, welcome to KITTY. Sign in with Google to enter your own chat and memory space. Virat manages the AI services; you never need a provider key.":"Sir, I'm KITTY. Your phone handles voice and commands; your laptop handles the thinking. Pair in Settings or connect your Google account, then try a conversation.",false,"","status");
    }
    private void render(){
        if(isDestroyed()||!visible)return;
        status.setText(!controller.busy&&prefs.speak()&&!controller.speaker.issue.isEmpty()?controller.speaker.issue:controller.phase);listen.setText(controller.voiceRunning?"Hey Kitty: on":"Hey Kitty: off");
        heard.setText(controller.voiceRunning?controller.voiceState+(controller.heard.isEmpty()?"":"\nHeard: "+controller.heard):"");
        micMeter.setVisibility(controller.voiceRunning?View.VISIBLE:View.GONE);micMeter.setProgress(Math.round(controller.micLevel*100));
        orb.active(controller.busy||controller.speaker.active());
        Set<String> ids=new HashSet<>();for(JSONObject t:controller.turns)ids.add(t.optString("id"));
        if(!ids.containsAll(replies.keySet())){chat.removeAllViews();replies.clear();completed.clear();}
        for(JSONObject t:controller.turns){
            String id=t.optString("id");TextView body=replies.get(id);
            if(body==null){bubble(t.optString("input"),true,"","");body=bubble(t.optString("reply"),false,"",t.optString("mode"));replies.put(id,body);}
            String answer=t.optString("reply");if(answer.isEmpty())answer="Waiting for KITTY…";
            if(!body.getText().toString().equals(answer))body.setText(answer);
            TextView label=(TextView)((LinearLayout)body.getParent()).getChildAt(0);String mode=t.optString("mode");
            String detail="KITTY  ·  "+mode.toUpperCase(Locale.ROOT);
            try{JSONObject meta=new JSONObject(t.optString("metadata","{}"));if(meta.has("first_token_ms"))detail+="  ·  first word "+String.format(Locale.ROOT,"%.1fs",meta.optDouble("first_token_ms")/1000);}catch(Exception ignored){}
            label.setText(detail);
            if("model".equals(t.optString("mode"))&&completed.add(id))addFeedback((LinearLayout)body.getParent(),id);
        }
        if(controller.voiceRunning)bindVoice();else unbindVoice();
    }
    @Override protected void onStart(){super.onStart();visible=true;controller.foreground=new WeakReference<>(this);controller.observe(changed);controller.sync();bindVoice();if(SystemClock.elapsedRealtime()-checkedAt>30000)refreshConnection(false);main.removeCallbacks(pollNotices);main.post(pollNotices);}
    @Override protected void onStop(){visible=false;main.removeCallbacks(pollNotices);controller.remove(changed);if(controller.foreground.get()==this)controller.foreground.clear();unbindVoice();super.onStop();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);}
    private TextView bubble(String message,boolean user,String id,String mode){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setBackground(bg(user?0xFF253C34:CARD,18));box.setPadding(dp(16),dp(14),dp(16),dp(14));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(user?dp(30):0,0,user?0:dp(18),dp(9));chat.addView(box,lp);
        TextView label=text(user?"YOU":"KITTY"+(mode!=null&&mode.equals("local")?"  ·  PHONE":""),9,user?MUTED:ACCENT);label.setLetterSpacing(.14f);box.addView(label);
        TextView body=text(message,15,INK);body.setAutoLinkMask(android.text.util.Linkify.WEB_URLS);body.setLinkTextColor(ACCENT);body.setTextIsSelectable(true);body.setPadding(0,dp(7),0,0);body.setLineSpacing(dp(2),1.06f);box.addView(body);
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
        if(prefs.cloud()){cloudUi.settings();return;}
        legacySettings();
    }
    private void offlineSetup(){
        new AlertDialog.Builder(this).setTitle("Offline speech").setItems(new String[]{"Download Indian English ZIP","Import speech model ZIP"},(d,i)->{
            if(i==0)startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip")));
            else{requestVoiceStop();Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");startActivityForResult(pick,70);}
        }).show();
    }
    private void legacySettings(){
        ScrollView container=new ScrollView(this);LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(20),dp(6),dp(20),dp(10));container.addView(form);
        form.addView(text("Pair my brain. Choose my voice. Give me the access I need, Sir.",14,INK));
        form.addView(button("Sign in with Google · Cloud KITTY",cloudUi::signIn));
        EditText url=field(form,"Laptop server URL",prefs.url(),false);
        EditText token=field(form,"Pairing token",prefs.token(),true);
        EditText country=field(form,"Country calling code",prefs.country(),false);
        CheckBox spoken=new CheckBox(this);spoken.setText("Speak replies");spoken.setChecked(prefs.speak());form.addView(spoken);
        CheckBox direct=new CheckBox(this);direct.setText("Direct calls after a clear command");direct.setChecked(prefs.directCalls());form.addView(direct);
        form.addView(text("Use your Tailscale HTTPS address for wireless access—even on mobile data. The laptop must stay awake. Chats sync to the paired brain and, if configured, Turso; voice audio and screen contents stay on the phone. Share guest tokens, never your owner token.",11,MUTED));
        form.addView(button("Check saved connection",()->worker.execute(()->{
            try{JSONObject s=BrainClient.request(prefs,"/v1/status",null);String result=s.optBoolean("model_ready")?"Sir, connected. Model: "+s.optString("model"):"Sir, KITTY connected; start the llama.cpp model server with alias "+s.optString("model")+".";main.post(()->{if(!isFinishing()&&!isDestroyed())new AlertDialog.Builder(this).setMessage(result).setPositiveButton("OK",null).show();});}
            catch(Exception e){main.post(()->{if(!isFinishing()&&!isDestroyed())new AlertDialog.Builder(this).setMessage("Sir, connection failed. Save your settings first, then check the laptop server and token.").setPositiveButton("OK",null).show();});}
        })));
        form.addView(button("Check for app updates",this::checkForUpdate));
        form.addView(text("KITTY checks release metadata over HTTPS, then verifies the APK checksum, package, version and signing certificate. Android asks before installing. Updates require the same signing key to preserve your chats.",11,MUTED));
        form.addView(button("System status & diagnostics",()->refreshConnection(true)));
        form.addView(button("Open beginner setup guide",()->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/psychspy7/KITTY.AI/blob/main/docs/START_HERE_V03.md")))));
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
        form.addView(text("Voice: start Hey Kitty here before opening YouTube. Speak ‘Hey Kitty’ clearly, pause, then your command. Keep media volume moderate or use earphones. ‘Play the first video’ selects the first identifiable visible YouTube result, not every layout. If recognition misses you, use Listen now in the microphone notification. English commands only with these packs; Hindi/Hinglish chat remains available through the brain. No raw audio is saved.",11,MUTED));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("KITTY Settings").setView(container).setPositiveButton("Save",null).setNegativeButton("Close",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try {
                String server=url.getText().toString().trim(),cc=country.getText().toString().replace("+","").trim();BrainClient.validateUrl(server);if(!cc.matches("[1-9][0-9]{0,3}"))throw new Exception("Use a valid country calling code.");
                prefs.token(token.getText().toString().trim());prefs.p.edit().putString("url",server).putString("country",cc).putBoolean("speak",spoken.isChecked()).putBoolean("direct_calls",direct.isChecked()).apply();
                if(direct.isChecked()&&checkSelfPermission(Manifest.permission.CALL_PHONE)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.CALL_PHONE},25);
                controller.sync();if(!prefs.speak())controller.speaker.stop();dialog.dismiss();Toast.makeText(this,"Settings saved, Sir.",Toast.LENGTH_SHORT).show();
                refreshConnection(false);
            }catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
        }));dialog.show();
    }
    private void refreshConnection(boolean show){
        checkedAt=SystemClock.elapsedRealtime();
        if(prefs.token().isEmpty()){connectionStatus.setText(prefs.cloud()?"○  Sign in with Google   ·   YOUR SPACE  ↗":"○  Pair your brain in Settings   ·   SETUP  ↗");if(show)settings();return;}
        connectionStatus.setText("◌  Checking brain connection…");
        worker.execute(()->{
            JSONObject result=null;try{result=BrainClient.request(prefs,"/v1/status",null);}catch(Exception ignored){}
            final JSONObject data=result;
            main.post(()->{
                if(isDestroyed()||isFinishing())return;diagnostics=data;if(data!=null&&prefs.cloud())prefs.p.edit().putBoolean("speech_ready",data.optBoolean("speech_ready")).apply();
                connectionStatus.setText(data==null?"○  Brain offline   ·   Phone commands ready  ↗":(data.optBoolean("model_ready")?"●  Brain ready":"◐  Brain online · model offline")+"   ·   "+data.optString("role","owner").toUpperCase(Locale.ROOT)+"  ↗");
                if(show){
                    JSONObject sync=data==null?null:data.optJSONObject("sync");
                    String description="APP  "+appVersionName()+" · build "+appVersionCode()+"\n\nBRAIN  "+(data==null?"Unreachable. Check the service address, sign-in and internet.":data.optString("version")+" · "+(data.optBoolean("model_ready")?"model ready":(prefs.cloud()?"admin: connect Groq in your console":"start START_MODEL_FAST.bat")))+"\n\nWEB RESEARCH  "+(data!=null&&data.optBoolean("web_ready")?"Configured. Try: research latest space news":"Not configured on brain")+"\n\nCLOUD MEMORY  "+(sync==null?"Unknown":sync.optString("state")+" · "+sync.optInt("pending")+" queued operations")+"\n\nOFFLINE SPEECH  "+(ModelInstaller.installed(this)?"Model installed":"Import a Vosk ZIP in Settings")+"\nMICROPHONE  "+(controller.voiceRunning?controller.voiceState:"Off · start Hey Kitty while KITTY is open")+"\nSCREEN CONTROL  "+(KittyAccessibilityService.instance==null?"Enable Accessibility in Settings":"Connected")+"\n\nTiming shown under replies measures model first-token time, not total speech latency.";
                    new AlertDialog.Builder(this).setTitle("System status").setMessage(description).setPositiveButton("Done",null).show();
                }
            });
        });
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
        message.append("KITTY will verify this APK before Android asks you to install it. Keep KITTY open during the download.");
        new AlertDialog.Builder(this).setTitle("Update available").setMessage(message.toString()).setNegativeButton("Later",null).setPositiveButton("Download & verify",(d,w)->{
            if(updating)return;updating=true;controller.note("Downloading and verifying update…");
            worker.execute(()->{try{java.io.File file=AppUpdater.download(getApplicationContext(),info);main.post(()->{updating=false;pendingApk=file;if(!isDestroyed())installUpdate();});}catch(Exception e){main.post(()->{updating=false;if(!isDestroyed())new AlertDialog.Builder(this).setTitle("Update not installed").setMessage(e.getMessage()).setPositiveButton("OK",null).show();});}});
        }).show();
    }
    private void installUpdate(){
        if(pendingApk==null||!pendingApk.exists())return;
        try{
            if(!getPackageManager().canRequestPackageInstalls()){
                new AlertDialog.Builder(this).setTitle("Allow KITTY updates").setMessage("Allow installation from KITTY on the next Android screen, then return here. Every update still needs your confirmation.").setPositiveButton("Open Android settings",(d,w)->startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())),71)).setNegativeButton("Later",null).show();return;
            }
            Uri apk=androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".updates",pendingApk);
            startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(apk,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        }catch(Exception e){Toast.makeText(this,"Android could not open the verified installer.",Toast.LENGTH_LONG).show();}
    }
    private void chooseVoice(){
        List<Voice> voices=controller.speaker.voices();
        if(voices.isEmpty()){Toast.makeText(this,"Download an offline English or Hindi TTS voice in Android settings first, Sir.",Toast.LENGTH_LONG).show();return;}
        new AlertDialog.Builder(this).setTitle("Choose and preview a voice").setItems(voices.stream().map(Voice::getName).toArray(String[]::new),(d,i)->controller.speaker.preview(voices.get(i))).show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==91&&result==RESULT_OK&&data!=null&&data.getData()!=null)cloudUi.saveTraining(data.getData());
        if(request==71&&getPackageManager().canRequestPackageInstalls())installUpdate();
        if(request==70&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();bubble("Sir, importing the offline model. This may take a moment.",false,"","status");worker.execute(()->{
                String message;try{ModelInstaller.install(this,uri);message="Sir, offline speech is ready. Tap Hey Kitty to start listening.";}catch(Exception e){message="Sir, model import failed: "+e.getMessage();}
                final String finalMessage=message;main.post(()->{controller.note(finalMessage);if(!isDestroyed())bubble(finalMessage,false,"","status");});
            });
        }
    }
    @Override public void onDestroy(){cloudUi.close();worker.shutdown();main.removeCallbacksAndMessages(null);super.onDestroy();}
}
