package com.kitty.ai;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.widget.*;
import androidx.credentials.*;
import androidx.credentials.exceptions.*;
import com.google.android.libraries.identity.googleid.*;
import org.json.*;
import java.util.concurrent.*;

/** Account and admin screens. All privileged operations are checked again by the server. */
final class CloudUi {
    private final Activity activity;private final Prefs prefs;private final ChatController chat;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService network=Executors.newSingleThreadExecutor();
    private boolean signingIn;
    private final Runnable update,offline;
    CloudUi(Activity a,Runnable updateAction,Runnable offlineAction){activity=a;prefs=new Prefs(a);chat=KittyApp.chat(a);update=updateAction;offline=offlineAction;}
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private LinearLayout form(){LinearLayout box=new LinearLayout(activity);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(22),dp(12),dp(22),dp(12));return box;}
    private TextView text(String value,int size){TextView v=new TextView(activity);v.setText(value);v.setTextColor(0xFFF3F5EF);v.setTextSize(size);v.setPadding(0,dp(8),0,dp(8));return v;}
    private Button button(String label,Runnable action){Button b=new Button(activity);b.setText(label);b.setAllCaps(false);b.setOnClickListener(v->action.run());return b;}
    private CheckBox toggle(LinearLayout box,String label,boolean value){CheckBox c=new CheckBox(activity);c.setText(label);c.setChecked(value);box.addView(c);return c;}
    private EditText field(LinearLayout box,String label,String value,boolean secret){box.addView(text(label,12));EditText e=new EditText(activity);e.setText(value);e.setSingleLine(true);e.setInputType(InputType.TYPE_CLASS_TEXT|(secret?InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));e.setSaveEnabled(!secret);box.addView(e);return e;}
    private void show(String title,LinearLayout box){ScrollView s=new ScrollView(activity);s.addView(box);new AlertDialog.Builder(activity).setTitle(title).setView(s).setPositiveButton("Done",null).show();}
    private void error(String message){if(!activity.isFinishing()&&!activity.isDestroyed())new AlertDialog.Builder(activity).setTitle("KITTY").setMessage(message).setPositiveButton("OK",null).show();}
    interface Result {void done(JSONObject result) throws Exception;}
    private void api(String path,JSONObject body,Result next){BrainClient.Session account=new BrainClient.Session(prefs);network.execute(()->{try{JSONObject result=BrainClient.request(account,path,body);main.post(()->{if(activity.isDestroyed()||!account.current(prefs))return;try{next.done(result);}catch(Exception e){error("Could not display the response. Please retry.");}});}catch(Exception e){main.post(()->error(e.getMessage()==null?"Connection unavailable":e.getMessage()));}});}
    void signIn(){
        if(signingIn)return;
        if(!prefs.url().startsWith("https://")){
            LinearLayout box=form();box.addView(text("Connect to the service address supplied by Virat. You never need a model API key.",14));EditText url=field(box,"KITTY service HTTPS address",BuildConfig.KITTY_SERVER_URL,false);
            new AlertDialog.Builder(activity).setTitle("Connect KITTY").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Continue",(d,w)->{try{String v=url.getText().toString().trim();BrainClient.validateUrl(v);if(!v.startsWith("https://"))throw new Exception("Use the HTTPS address supplied by the admin.");prefs.p.edit().putString("url",v).putBoolean("cloud",true).apply();signIn();}catch(Exception e){error(e.getMessage());}}).show();return;
        }
        signingIn=true;Toast.makeText(activity,"Preparing Google sign-in…",Toast.LENGTH_SHORT).show();
        network.execute(()->{try{JSONObject challenge=BrainClient.publicRequest(prefs,"/v1/auth/challenge",new JSONObject());main.post(()->google(challenge));}catch(Exception e){main.post(()->{signingIn=false;error(e.getMessage());});}});
    }
    private void google(JSONObject challenge){
        if(activity.isDestroyed()){signingIn=false;return;}
        GetSignInWithGoogleOption option=new GetSignInWithGoogleOption.Builder(challenge.optString("client_id")).setNonce(challenge.optString("nonce")).build();
        GetCredentialRequest request=new GetCredentialRequest.Builder().addCredentialOption(option).build();
        CredentialManager.create(activity).getCredentialAsync(activity,request,null,activity.getMainExecutor(),new CredentialManagerCallback<GetCredentialResponse,GetCredentialException>(){
            @Override public void onResult(GetCredentialResponse result){
                try{
                    Credential c=result.getCredential();if(!(c instanceof CustomCredential))throw new Exception("Unexpected Google credential");
                    GoogleIdTokenCredential id=GoogleIdTokenCredential.createFrom(c.getData());
                    network.execute(()->{try{
                        JSONObject response=BrainClient.publicRequest(prefs,"/v1/auth/google",new JSONObject().put("challenge",challenge.optString("challenge")).put("id_token",id.getIdToken()));
                        main.post(()->{try{if(activity.isDestroyed())return;prefs.token(response.getString("token"));prefs.account(response.getJSONObject("account"));chat.accountChanged();NoticeWorker.schedule(activity);api("/v1/status",null,status->prefs.p.edit().putBoolean("speech_ready",status.optBoolean("speech_ready")).apply());signingIn=false;Toast.makeText(activity,"Welcome to KITTY, Sir.",Toast.LENGTH_LONG).show();settings();}catch(Exception e){signingIn=false;error("Could not save your sign-in. Please retry.");}});
                    }catch(Exception e){main.post(()->{signingIn=false;error(e.getMessage());});}});
                }catch(Exception e){signingIn=false;error("Google sign-in returned an unsupported credential. Update Google Play services and retry.");}
            }
            @Override public void onError(GetCredentialException e){signingIn=false;error("Google sign-in was cancelled or unavailable. Check your Google account, Play services and the app's OAuth setup.");}
        });
    }
    void settings(){
        if(prefs.accountId().isEmpty()){
            LinearLayout box=form();box.addView(text("Your personal intelligence, wherever you are.",22));box.addView(text("Sign in with Google to chat. Virat manages the AI services, so you never paste provider keys.",14));box.addView(button("Sign in with Google",this::signIn));box.addView(button("Check for app updates",update));box.addView(button("Offline speech setup",offline));show("Welcome to KITTY",box);return;
        }
        api("/v1/me",null,account->{prefs.account(account);userSettings(account);});
    }
    private void userSettings(JSONObject account){
        LinearLayout box=form();box.addView(text("YOUR SPACE",11));box.addView(text(account.optString("name","Sir"),22));box.addView(text(account.optString("email"),12));
        CheckBox speak=toggle(box,"Speak replies",prefs.speak()),cloud=toggle(box,"Use KITTY's cloud voice when available",prefs.p.getBoolean("cloud_voice",true)),follow=toggle(box,"Listen for a follow-up for 10 seconds",prefs.p.getBoolean("follow_up",true));
        box.addView(button("Save voice preferences",()->{prefs.p.edit().putBoolean("speak",speak.isChecked()).putBoolean("cloud_voice",cloud.isChecked()).putBoolean("follow_up",follow.isChecked()).apply();if(!speak.isChecked())chat.speaker.stop();Toast.makeText(activity,"Saved",Toast.LENGTH_SHORT).show();}));
        box.addView(text("Chats are saved on this phone. Signed-in messages also pass through KITTY's server and the AI provider to generate replies. Optional Drive backup stores a copy in Virat's Drive. Sharing for training is a separate choice; it does not train the model automatically.",12));
        CheckBox backup=toggle(box,"Back up my chat and memory to Virat's Drive",account.optBoolean("backup"));
        CheckBox training=toggle(box,"Share reviewed replies for future KITTY training",account.optBoolean("training"));
        box.addView(text("Drive: "+account.optJSONObject("drive"),11));
        box.addView(button("Save backup choices",()->{try{api("/v1/consent",new JSONObject().put("backup",backup.isChecked()).put("training",training.isChecked()),r->{prefs.account(r);error("Choices saved. If you turn backup off, KITTY queues deletion of its Drive copy. Previously downloaded training exports cannot be recalled.");});}catch(JSONException e){error("Could not save choices");}}));
        box.addView(button("View notices",this::notices));
        box.addView(button("Check for app updates",update));
        box.addView(button("Show my recent chats",()->chat.history(true)));
        box.addView(button("Microphone and notification permissions",()->{java.util.ArrayList<String> needed=new java.util.ArrayList<>();needed.add(Manifest.permission.RECORD_AUDIO);if(Build.VERSION.SDK_INT>=33)needed.add(Manifest.permission.POST_NOTIFICATIONS);needed.removeIf(p->activity.checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED);if(!needed.isEmpty())activity.requestPermissions(needed.toArray(new String[0]),24);}));
        box.addView(button("Enable screen control",()->new AlertDialog.Builder(activity).setTitle("Allow phone commands?").setMessage("KITTY Accessibility can read visible app labels and perform taps, typing and scrolling when you request them. It does not upload your screen to the model. Enable only if you want these commands.").setNegativeButton("Cancel",null).setPositiveButton("Open settings",(d,w)->activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).show()));
        box.addView(button("Background listening and battery settings",()->activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+activity.getPackageName())))));
        box.addView(button("Offline speech setup",offline));
        if(account.optString("role").equals("admin"))box.addView(button("Open admin console",this::admin));
        box.addView(button("Sign out",()->new AlertDialog.Builder(activity).setMessage("Sign out of this phone? Your local archive remains isolated to your account.").setNegativeButton("Cancel",null).setPositiveButton("Sign out",(d,w)->signOut()).show()));
        show("KITTY Settings",box);
    }
    private void signOut(){
        BrainClient.Session account=new BrainClient.Session(prefs);chat.stop();activity.stopService(new Intent(activity,VoiceService.class));
        try{
            prefs.signOut();chat.accountChanged();NoticeWorker.cancel(activity);
            CredentialManager.create(activity).clearCredentialStateAsync(new ClearCredentialStateRequest(),null,activity.getMainExecutor(),new CredentialManagerCallback<Void,ClearCredentialException>(){public void onResult(Void v){}public void onError(ClearCredentialException e){}});
            error("Signed out of this phone.");
            network.execute(()->{try{BrainClient.request(account,"/v1/logout",new JSONObject());}catch(Exception ignored){}});
        }catch(Exception e){error("Sign-out could not be saved. Retry.");}
    }
    void notices(){api("/v1/notices",null,r->{LinearLayout box=form();JSONArray list=r.optJSONArray("notices");if(list==null||list.length()==0)box.addView(text("No notices yet.",14));else for(int i=0;i<list.length();i++){JSONObject n=list.getJSONObject(i);box.addView(text(n.optString("title"),18));box.addView(text(n.optString("body"),14));}show("From KITTY",box);});}
    private void admin(){api("/v1/admin/settings",null,r->{
        LinearLayout box=form();box.addView(text("CONTROL ROOM",11));box.addView(text("KITTY Administration",23));box.addView(text("API keys remain encrypted on the backend. Blank fields preserve the existing key. Never paste keys into chat.",12));
        EditText groq=field(box,"Groq key · "+(r.optBoolean("groq_configured")?"configured":"not connected"),"",true),model=field(box,"Groq chat model",r.optString("groq_model"),false);
        EditText gemini=field(box,"Gemini speech key · "+(r.optBoolean("gemini_configured")?"configured":"not connected"),"",true),speech=field(box,"Gemini speech model",r.optString("gemini_model"),false),voice=field(box,"Speech voice",r.optString("voice","Kore"),false);
        EditText character=field(box,"Additional KITTY character preferences",r.optString("character"),false);character.setSingleLine(false);character.setMinLines(3);
        box.addView(button("Save providers and character",()->{try{JSONObject b=new JSONObject().put("groq_model",model.getText().toString().trim()).put("gemini_model",speech.getText().toString().trim()).put("voice",voice.getText().toString().trim()).put("character",character.getText().toString());if(groq.length()>0)b.put("groq_key",groq.getText().toString());if(gemini.length()>0)b.put("gemini_key",gemini.getText().toString());api("/v1/admin/settings",b,s->{prefs.p.edit().putBoolean("speech_ready",s.optBoolean("gemini_configured")).apply();groq.setText("");gemini.setText("");error("Provider settings saved. Test the selected model before sharing the app.");});}catch(Exception e){error("Check your settings");}}));
        box.addView(button("Check available Groq models",()->api("/v1/admin/models",null,s->error(s.optJSONArray("models").toString()))));
        box.addView(button("Disconnect provider keys",()->new AlertDialog.Builder(activity).setMessage("Remove both saved provider keys?").setNegativeButton("Cancel",null).setPositiveButton("Remove",(d,w)->{try{api("/v1/admin/settings",new JSONObject().put("groq_key","").put("gemini_key",""),s->error("Provider keys removed."));}catch(Exception ignored){}}).show()));
        box.addView(text("Drive: "+(r.optBoolean("drive_connected")?"connected":"not connected")+" · authorized owner "+r.optString("backup_owner"),12));
        box.addView(button("Connect admin Google Drive",()->api("/v1/admin/drive",new JSONObject(),s->activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(s.getString("url")))))));
        EditText title=field(box,"Notice title","",false),body=field(box,"Notice message","",false);body.setSingleLine(false);body.setMinLines(3);
        box.addView(button("Publish notice to all users",()->new AlertDialog.Builder(activity).setTitle("Publish notice?").setMessage(title.getText()+"\n\n"+body.getText()).setNegativeButton("Cancel",null).setPositiveButton("Publish",(d,w)->{try{api("/v1/admin/notice",new JSONObject().put("title",title.getText().toString()).put("body",body.getText().toString()),s->{title.setText("");body.setText("");error("Notice published. Active apps refresh shortly; background delivery follows Android's scheduled checks.");});}catch(Exception e){error("Check the notice");}}).show()));
        box.addView(button("Export reviewed training sample",()->activity.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"KITTY-reviewed-training.jsonl"),91)));
        box.addView(text("Updates require a newer APK signed with the same retained signing key. Publishing a notice does not publish a signed app release. Follow the release guide for the version/tag process.",12));
        box.addView(button("Open release guide",()->activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/psychspy7/KITTY.AI/blob/main/docs/UPDATES_AND_RELEASES.md")))));
        show("Admin console",box);
    });}
    void saveTraining(Uri destination){
        BrainClient.Session account=new BrainClient.Session(prefs);
        if(!prefs.admin()){error("Admin account required");return;}
        network.execute(()->{try{
            JSONArray examples=BrainClient.request(account,"/v1/admin/export",null).getJSONArray("examples");
            if(!account.current(prefs))return;
            try(java.io.OutputStream out=activity.getContentResolver().openOutputStream(destination,"wt")){if(out==null)throw new java.io.IOException();for(int i=0;i<examples.length();i++)out.write((examples.getJSONObject(i).toString()+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            main.post(()->error("Saved "+examples.length()+" reviewed examples. This sample does not train or modify the model."));
        }catch(Exception e){main.post(()->error("Training sample could not be saved. Check your connection and file location."));}});
    }
    void close(){network.shutdownNow();main.removeCallbacksAndMessages(null);}
}
