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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    public static volatile boolean visible;
    public static MainActivity active;
    private static final int BG=0xFF0C0D12,CARD=0xFF191A23,INK=0xFFF4F0FF,MUTED=0xFFAAA6B9,ACCENT=0xFFC5B6FF;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private LinearLayout chat;private ScrollView scroll;private EditText input;private TextView status;private OrbView orb;
    private Prefs prefs;private TextToSpeech tts;private SpeechRecognizer recognizer;private boolean ttsReady,busy,recording;
    private Button listen;
    // VoiceService shares this process and delivers on the main thread. No
    // externally reachable broadcast channel is needed for private chat events.
    void onVoiceEvent(String user,String reply,String responseId,String mode){
        if(user!=null&&!user.isEmpty())bubble(user,true,"","");
        if(reply!=null&&!reply.isEmpty())bubble(reply,false,responseId,mode);
        updateStatus();
    }
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    private Button button(String title,Runnable action){Button b=new Button(this);b.setText(title);b.setTextSize(12);b.setAllCaps(false);b.setTextColor(INK);b.setBackground(bg(CARD,12));b.setMinHeight(dp(42));b.setPadding(dp(12),dp(7),dp(12),dp(7));b.setOnClickListener(v->action.run());return b;}
    private void addButton(LinearLayout row,Button b){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);lp.setMargins(dp(3),0,dp(3),0);row.addView(b,lp);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);prefs=new Prefs(this);
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
        status=text("PERSONAL SYSTEM  /  ALPHA 0.1",10,MUTED);status.setPadding(0,dp(5),0,0);titles.addView(status);
        header.addView(titles,new LinearLayout.LayoutParams(0,dp(58),1));
        header.addView(button("Settings",this::settings),new LinearLayout.LayoutParams(dp(85),dp(42)));root.addView(header);
        orb=new OrbView(this);root.addView(orb,new LinearLayout.LayoutParams(-1,dp(142)));
        TextView greeting=text("At your service, Sir.",24,INK);greeting.setGravity(Gravity.CENTER);greeting.setTypeface(null,Typeface.BOLD);root.addView(greeting);
        TextView subtitle=text("A little wit. A mind of your own.",12,MUTED);subtitle.setGravity(Gravity.CENTER);subtitle.setPadding(0,dp(7),0,dp(15));root.addView(subtitle);
        LinearLayout chips=new LinearLayout(this);
        addButton(chips,button("YouTube",()->send("open YouTube")));
        addButton(chips,button("Weather",()->{input.setText("weather in ");input.setSelection(input.length());input.requestFocus();}));
        addButton(chips,button("Memory",()->send("show memories")));root.addView(chips);
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
        addButton(bottom,button("New chat",()->{prefs.newSession();chat.removeAllViews();bubble("Sir, a fresh conversation. The memories you saved are still here.",false,"","status");}));root.addView(bottom);
        bubble("Sir, I'm KITTY. Open Settings to pair my laptop brain. App commands can already work on this phone. Try ‘open YouTube’ or ‘battery’.",false,"","status");
        tts=new TextToSpeech(this,code->{ttsReady=code==TextToSpeech.SUCCESS;if(ttsReady)Speech.configure(tts,prefs);});
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
            public void onStart(String id){}
            public void onDone(String id){main.post(()->{if(VoiceService.instance!=null)VoiceService.instance.resume();});}
            public void onError(String id){onDone(id);}
        });
        consumeIntent(getIntent());
    }
    private void updateStatus(){listen.setText(VoiceService.instance==null?"Hey Kitty: off":"Hey Kitty: on");orb.active(busy||recording||VoiceService.instance!=null);}
    @Override protected void onStart(){super.onStart();visible=true;active=this;updateStatus();}
    @Override protected void onStop(){visible=false;if(active==this)active=null;if(recognizer!=null){recognizer.cancel();recording=false;}super.onStop();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);consumeIntent(intent);}
    private void consumeIntent(Intent i){
        // Launcher activity is exported. Only a one-time notification capability
        // created inside this app may carry a pending phone action.
        if(i==null)return;
        String nonce=i.getStringExtra("action_nonce");i.removeExtra("action_nonce");
        String expected=prefs.p.getString("pending_action_nonce","");
        if(nonce==null||expected.isEmpty()||!expected.equals(nonce))return;
        String value=prefs.p.getString("pending_action_command","");
        prefs.p.edit().remove("pending_action_nonce").remove("pending_action_command").commit();
        if(!value.isEmpty())main.post(()->send(value));
    }
    private void bubble(String message,boolean user,String id,String mode){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setBackground(bg(user?0xFF29243B:CARD,14));box.setPadding(dp(14),dp(11),dp(14),dp(11));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(user?dp(30):0,0,user?0:dp(18),dp(9));chat.addView(box,lp);
        TextView label=text(user?"YOU":"KITTY"+(mode!=null&&mode.equals("local")?"  ·  PHONE":""),9,user?MUTED:ACCENT);label.setLetterSpacing(.14f);box.addView(label);
        TextView body=text(message,14,INK);body.setTextIsSelectable(true);body.setPadding(0,dp(5),0,0);body.setLineSpacing(dp(2),1.06f);box.addView(body);
        if(!user&&id!=null&&!id.isEmpty()&&"model".equals(mode)){
            LinearLayout feedback=new LinearLayout(this);feedback.setPadding(0,dp(5),0,0);
            Button good=button("Good reply",()->feedback(id,1,""));Button fix=button("Correct it",()->{
                EditText correction=new EditText(this);correction.setHint("What should KITTY have said?");
                new AlertDialog.Builder(this).setTitle("Teach KITTY, Sir").setView(correction).setPositiveButton("Save correction",(d,w)->feedback(id,-1,correction.getText().toString())).setNegativeButton("Cancel",null).show();
            });addButton(feedback,good);addButton(feedback,fix);box.addView(feedback);
        }
        scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));
    }
    private void feedback(String id,int rating,String correction){worker.execute(()->{try{BrainClient.request(prefs,"/v1/feedback",new JSONObject().put("response_id",id).put("rating",rating).put("correction",correction));main.post(()->Toast.makeText(this,"Saved for review, Sir. Model weights haven't changed.",Toast.LENGTH_LONG).show());}catch(Exception e){main.post(()->Toast.makeText(this,"Couldn't save feedback. Check the laptop connection.",Toast.LENGTH_LONG).show());}});}
    private void say(String answer){
        if(!prefs.speak())return;
        if(VoiceService.instance!=null){VoiceService.instance.say(answer);return;}
        if(ttsReady)tts.speak(answer.length()>3500?answer.substring(0,3500):answer,TextToSpeech.QUEUE_FLUSH,null,"kitty");
    }
    private void sendInput(){String value=input.getText().toString().trim();if(value.isEmpty())return;if(busy){Toast.makeText(this,"One moment, Sir.",Toast.LENGTH_SHORT).show();return;}input.setText("");send(value);}
    private void send(String value){
        if(busy){Toast.makeText(this,"One moment, Sir.",Toast.LENGTH_SHORT).show();return;}
        bubble(value,true,"","");Action action=Router.parse(value);
        if(action!=null){Actions.execute(this,action,answer->{bubble(answer,false,"","local");say(answer);});return;}
        busy=true;status.setText("LAPTOP BRAIN  /  THINKING");updateStatus();
        worker.execute(()->{
            JSONObject result=null;String error=null;
            try{result=BrainClient.chat(prefs,value);}catch(Exception e){error="Sir, I couldn't reach my laptop brain. Check Settings, the pairing token, and that the laptop server is running.";}
            final JSONObject response=result;final String failure=error;
            main.post(()->{
                if(isFinishing()||isDestroyed())return;
                busy=false;status.setText(response==null?"LAPTOP BRAIN  /  UNREACHABLE":"PERSONAL SYSTEM  /  CONNECTED");updateStatus();
                String answer=response==null?failure:response.optString("reply","Sir, the laptop returned an empty reply.");
                // This channel displays model replies; only explicit user commands execute phone actions.
                bubble(answer,false,response==null?"":response.optString("response_id"),response==null?"error":response.optString("mode"));say(answer);
            });
        });
    }
    private void microphone(){
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},21);return;}
        if(VoiceService.instance!=null){VoiceService.instance.arm();return;}
        if(recording){if(recognizer!=null)recognizer.stopListening();return;}
        if(Build.VERSION.SDK_INT<31||!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)){
            if(ModelInstaller.installed(this)){startForegroundService(new Intent(this,VoiceService.class).putExtra("once",true));main.postDelayed(this::updateStatus,500);}
            else bubble("Sir, this phone has no available on-device speech recognizer. Import the Vosk model in Settings to enable offline voice. You can keep typing.",false,"","status");
            return;
        }
        if(tts!=null)tts.stop();
        if(recognizer!=null)recognizer.destroy();
        recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
        recognizer.setRecognitionListener(new android.speech.RecognitionListener(){
            public void onReadyForSpeech(Bundle b){recording=true;status.setText("LISTENING  /  GO AHEAD, SIR");updateStatus();}
            public void onBeginningOfSpeech(){}public void onRmsChanged(float f){}public void onBufferReceived(byte[] b){}public void onEndOfSpeech(){}public void onPartialResults(Bundle b){}public void onEvent(int a,Bundle b){}
            public void onError(int e){recording=false;updateStatus();status.setText("VOICE  /  READY TO RETRY");bubble("Sir, I didn't get that. Try again, or import the Vosk model if your offline language pack is missing. Voice error "+e+".",false,"","status");}
            public void onResults(Bundle b){recording=false;updateStatus();ArrayList<String> words=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(words!=null&&!words.isEmpty())send(words.get(0));}
        });
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-IN");i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);recognizer.startListening(i);
    }
    private void toggleListening(){
        if(VoiceService.instance!=null){stopService(new Intent(this,VoiceService.class));main.postDelayed(this::updateStatus,500);return;}
        if(!ModelInstaller.installed(this)){bubble("Sir, import a small Vosk English model in Settings first. Listening runs locally on your phone.",false,"","status");return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},22);return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},23);return;}
        if(recognizer!=null)recognizer.cancel();if(tts!=null)tts.stop();
        startForegroundService(new Intent(this,VoiceService.class));main.postDelayed(this::updateStatus,500);
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED){if(code==21)microphone();else if(code==22||code==23)toggleListening();}}
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
        form.addView(text("With direct calls off, KITTY opens the dialer. Internet access is already enabled. HTTP over Wi-Fi is unencrypted; USB is the easiest first connection.",11,MUTED));
        form.addView(button("Check saved connection",()->worker.execute(()->{
            try{JSONObject s=BrainClient.request(prefs,"/v1/status",null);String result=s.optBoolean("model_ready")?"Sir, connected. Model: "+s.optString("model"):"Sir, KITTY connected; start the llama.cpp model server with alias "+s.optString("model")+".";main.post(()->new AlertDialog.Builder(this).setMessage(result).setPositiveButton("OK",null).show());}
            catch(Exception e){main.post(()->new AlertDialog.Builder(this).setMessage("Sir, connection failed. Save your settings first, then check the laptop server and token.").setPositiveButton("OK",null).show());}
        })));
        form.addView(button("Grant microphone, contacts and call access",()->{
            ArrayList<String> wanted=new ArrayList<>(Arrays.asList(Manifest.permission.RECORD_AUDIO,Manifest.permission.READ_CONTACTS,Manifest.permission.CALL_PHONE));if(Build.VERSION.SDK_INT>=33)wanted.add(Manifest.permission.POST_NOTIFICATIONS);
            wanted.removeIf(p->checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED);if(!wanted.isEmpty())requestPermissions(wanted.toArray(new String[0]),24);else Toast.makeText(this,"These permissions are already granted, Sir.",Toast.LENGTH_LONG).show();
        }));
        form.addView(button("Enable screen control (Accessibility)",()->new AlertDialog.Builder(this).setTitle("Screen control").setMessage("KITTY can read the active screen locally and tap, type, scroll or navigate when you tell her to. Screen contents are not uploaded. Android will ask you to enable KITTY AI on the next screen.").setPositiveButton("Open Settings",(d,w)->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).setNegativeButton("Cancel",null).show()));
        form.addView(button("Choose an installed voice",this::chooseVoice));
        form.addView(button("Download offline speech model",()->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip")))));
        form.addView(button(ModelInstaller.installed(this)?"Replace offline speech model":"Import offline speech model ZIP",()->{stopService(new Intent(this,VoiceService.class));Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.addCategory(Intent.CATEGORY_OPENABLE);pick.setType("*/*");startActivityForResult(pick,70);}));
        form.addView(button("App permissions & battery settings",()->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))));
        form.addView(text("Voice: English commands in this alpha. General chat can use Hindi/Hinglish through the laptop model. A female speaking voice depends on the voices installed on this phone. No root or device-owner enrollment is performed.",11,MUTED));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("KITTY Settings").setView(container).setPositiveButton("Save",null).setNegativeButton("Close",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try {
                String server=url.getText().toString().trim(),cc=country.getText().toString().replace("+","").trim();BrainClient.validateUrl(server);if(!cc.matches("[1-9][0-9]{0,3}"))throw new Exception("Use a valid country calling code.");
                prefs.token(token.getText().toString().trim());prefs.p.edit().putString("url",server).putString("country",cc).putBoolean("speak",spoken.isChecked()).putBoolean("direct_calls",direct.isChecked()).apply();
                if(direct.isChecked()&&checkSelfPermission(Manifest.permission.CALL_PHONE)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.CALL_PHONE},25);
                dialog.dismiss();Toast.makeText(this,"Settings saved, Sir.",Toast.LENGTH_SHORT).show();
            }catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
        }));dialog.show();
    }
    private void chooseVoice(){
        if(!ttsReady||tts.getVoices()==null){Toast.makeText(this,"Install a text-to-speech engine and offline voices in Android settings.",Toast.LENGTH_LONG).show();return;}
        List<Voice> voices=new ArrayList<>();for(Voice v:tts.getVoices())if(!v.isNetworkConnectionRequired()&&(v.getLocale().getLanguage().equals("en")||v.getLocale().getLanguage().equals("hi")))voices.add(v);
        voices.sort(Comparator.comparing(Voice::getName));
        if(voices.isEmpty()){Toast.makeText(this,"Download an offline English or Hindi voice first, Sir.",Toast.LENGTH_LONG).show();return;}
        new AlertDialog.Builder(this).setTitle("Choose and preview a voice").setItems(voices.stream().map(Voice::getName).toArray(String[]::new),(d,i)->{prefs.p.edit().putString("voice",voices.get(i).getName()).apply();tts.setVoice(voices.get(i));tts.speak("At your service, Sir. Try not to make this a full time rescue operation.",TextToSpeech.QUEUE_FLUSH,null,"preview");Toast.makeText(this,"Voice saved. Restart listening mode to update its voice.",Toast.LENGTH_LONG).show();}).show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==70&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();bubble("Sir, importing the offline model. This may take a moment.",false,"","status");worker.execute(()->{
                String message;try{ModelInstaller.install(this,uri);message="Sir, offline speech is ready. Tap Hey Kitty to start listening.";}catch(Exception e){message="Sir, model import failed: "+e.getMessage();}
                final String finalMessage=message;main.post(()->bubble(finalMessage,false,"","status"));
            });
        }
    }
    @Override public void onDestroy(){if(active==this)active=null;if(recognizer!=null)recognizer.destroy();if(tts!=null){tts.stop();tts.shutdown();}worker.shutdownNow();main.removeCallbacksAndMessages(null);super.onDestroy();}
}
