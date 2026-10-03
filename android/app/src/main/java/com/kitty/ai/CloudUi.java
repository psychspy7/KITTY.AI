package com.kitty.ai;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.widget.*;
import androidx.credentials.*;
import androidx.credentials.exceptions.*;
import com.google.android.libraries.identity.googleid.*;
import com.google.firebase.auth.*;
import org.json.*;
import java.util.concurrent.*;

/** Owner console stays off the user screen. Every privileged endpoint checks Firebase on the server. */
final class CloudUi {
    private final Activity a;private final Design d;private final Prefs prefs;private final Runnable changed;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService network=Executors.newFixedThreadPool(2);
    private boolean signingIn,closed;
    private final java.util.List<AlertDialog> dialogs=new java.util.ArrayList<>();
    CloudUi(Activity activity,Runnable change){a=activity;d=new Design(a);prefs=new Prefs(a);changed=change;}
    private LinearLayout form(){LinearLayout l=d.column();l.setPadding(d.dp(22),d.dp(8),d.dp(22),d.dp(16));return l;}
    private void show(String title,LinearLayout box){if(closed)return;ScrollView scroll=new ScrollView(a);scroll.addView(box);AlertDialog dialog=new AlertDialog.Builder(a).setTitle(title).setView(scroll).setPositiveButton("Done",null).create();dialogs.add(dialog);dialog.show();}
    void message(String value){if(!closed&&!a.isFinishing())new AlertDialog.Builder(a).setTitle("KITTY").setMessage(value).setPositiveButton("OK",null).show();}
    interface Result {void done(JSONObject value) throws Exception;}
    private void api(String path,JSONObject body,Result result){
        BrainClient.Session account=new BrainClient.Session(prefs);
        network.execute(()->{try{JSONObject value=BrainClient.request(account,path,body);main.post(()->{if(closed||!account.current(prefs))return;try{result.done(value);}catch(Exception e){message("Could not display the response. Please retry.");}});}catch(Exception e){main.post(()->{if(account.current(prefs))message(e.getMessage()==null?"Service unavailable":e.getMessage());});}});
    }
    void refreshAccount(){
        if(!prefs.signedIn()||closed)return;
        prefs.clearRole();changed.run();
        api("/v1/me",null,value->{prefs.account(value,prefs.accountId());changed.run();});
        api("/v1/status",null,value->prefs.p.edit().putBoolean("speech_ready",value.optBoolean("speech_ready")).apply());
    }
    void signIn(){
        if(signingIn||closed)return;
        if(!KittyApp.configured()||KittyApp.auth()==null){message("Service setup is pending. Virat needs to connect Firebase and the cloud service before sign-in is available.");return;}
        signingIn=true;
        GetSignInWithGoogleOption option=new GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build();
        GetCredentialRequest request=new GetCredentialRequest.Builder().addCredentialOption(option).build();
        CredentialManager.create(a).getCredentialAsync(a,request,null,a.getMainExecutor(),new CredentialManagerCallback<GetCredentialResponse,GetCredentialException>(){
            public void onResult(GetCredentialResponse response){
                if(closed){signingIn=false;return;}
                try{
                    Credential credential=response.getCredential();
                    if(!(credential instanceof CustomCredential)||!credential.getType().equals(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL))throw new IllegalArgumentException();
                    String googleToken=GoogleIdTokenCredential.createFrom(credential.getData()).getIdToken();
                    KittyApp.auth().signInWithCredential(GoogleAuthProvider.getCredential(googleToken,null)).addOnCompleteListener(task->{
                        signingIn=false;if(closed)return;
                        if(!task.isSuccessful())message("Google sign-in could not complete. Ask Virat to check Firebase, the APK signing fingerprint and Google Play services.");
                    });
                }catch(Exception e){signingIn=false;message("Google returned an unsupported credential. Update Google Play services and retry.");}
            }
            public void onError(GetCredentialException e){signingIn=false;if(e instanceof GetCredentialCancellationException)return;message("Google sign-in is unavailable. Check your Google account and Play services. Virat may need to finish Firebase setup.");}
        });
    }
    void settings(){
        LinearLayout box=form();FirebaseUser u=KittyApp.auth()==null?null:KittyApp.auth().getCurrentUser();
        box.addView(d.label("YOUR ACCOUNT"));d.gap(box,8);box.addView(d.text(u==null?"Not signed in":u.getEmail(),15,Design.TEXT));d.gap(box,18);
        Switch speak=new Switch(a);speak.setText("Read replies aloud");speak.setTextColor(Design.TEXT);speak.setChecked(prefs.speak());speak.setOnCheckedChangeListener((v,on)->prefs.speak(on));box.addView(speak);
        box.addView(d.text("Playback only. KITTY does not record audio or listen in the background.",12,Design.MUTED));d.gap(box,12);
        box.addView(d.button("Check for updates",false,this::updates));
        box.addView(d.button("Privacy & memory",false,this::privacy));
        if(prefs.admin())box.addView(d.button("Admin console",true,this::admin));
        box.addView(d.button("Sign out",false,()->{prefs.clearRole();KittyApp.chat(a).stop();KittyApp.auth().signOut();CredentialManager.create(a).clearCredentialStateAsync(new ClearCredentialStateRequest(),null,a.getMainExecutor(),new CredentialManagerCallback<Void,ClearCredentialException>(){public void onResult(Void v){}public void onError(ClearCredentialException e){}});changed.run();}));
        d.gap(box,12);box.addView(d.text("KITTY AI "+BuildConfig.VERSION_NAME+"\nMade by Virat with the help of Kitty Corp.",12,Design.MUTED));show("Settings",box);
    }
    private void privacy(){api("/v1/me",null,value->{
        LinearLayout box=form();box.addView(d.text("Chats are saved privately on this phone for your account and sent to the cloud service to generate replies. Google login separates users. Your API keys are never required.",14,Design.TEXT));d.gap(box,12);
        box.addView(d.text("Use ‘remember that …’, ‘show my memory’ and ‘forget memory ID’ in chat. Personal memory cannot edit KITTY’s core character. Other accounts cannot see your local history.",14,Design.MUTED));d.gap(box,12);
        CheckBox backup=new CheckBox(a);backup.setText("Allow chat backup to admin Google Drive");backup.setChecked(value.optBoolean("backup"));box.addView(backup);
        CheckBox training=new CheckBox(a);training.setText("Allow reviewed chats to be exported for training");training.setChecked(value.optBoolean("training"));box.addView(training);
        box.addView(d.text("Both are optional and off by default. Saving chats does not automatically fine-tune a model. Backups require the admin to connect Drive.",12,Design.MUTED));
        box.addView(d.button("Save privacy choices",true,()->{try{api("/v1/consent",new JSONObject().put("backup",backup.isChecked()).put("training",training.isChecked()),r->message("Privacy choices saved."));}catch(Exception ignored){}}));show("Privacy & memory",box);
    });}
    void notices(){api("/v1/notices",null,value->{LinearLayout box=form();JSONArray notices=value.optJSONArray("notices");if(notices==null||notices.length()==0)box.addView(d.text("You’re all caught up. Updates from Virat will appear here.",15,Design.MUTED));else for(int i=0;i<notices.length();i++){JSONObject n=notices.getJSONObject(i);box.addView(d.label("FROM KITTY"));d.gap(box,8);box.addView(d.text(n.optString("title"),20,Design.TEXT));d.gap(box,8);box.addView(d.text(n.optString("body"),15,Design.MUTED));d.gap(box,24);}show("Inbox",box);});}
    void updates(){network.execute(()->{try{AppUpdater.UpdateInfo info=AppUpdater.fetch();main.post(()->{if(closed)return;if(info.code<=BuildConfig.VERSION_CODE){message("You’re on KITTY "+BuildConfig.VERSION_NAME+". No newer published release is available.");return;}new AlertDialog.Builder(a).setTitle("KITTY "+info.version+" is available").setMessage(info.notes+"\n\nThe official release opens in your browser. Android handles installation.").setNegativeButton("Later",null).setPositiveButton("Open release",(v,w)->open(info.url)).show();});}catch(Exception e){main.post(()->message("Could not check for updates. Please retry later."));}});}
    private EditText field(LinearLayout box,String title,String value,boolean secret){box.addView(d.text(title,12,Design.MUTED));d.gap(box,6);EditText e=d.field(title,value,secret);box.addView(e);d.gap(box,14);return e;}
    private void admin(){if(!prefs.admin())return;api("/v1/admin/settings",null,value->{
        LinearLayout box=form();box.addView(d.label("OWNER CONTROL ROOM"));d.gap(box,12);box.addView(d.text("API keys stay encrypted on the cloud server. Leave a key blank to retain it. Users see none of these controls.",13,Design.MUTED));d.gap(box,20);
        EditText groq=field(box,"Groq key · "+(value.optBoolean("groq_configured")?"connected":"not connected"),"",true);
        EditText model=field(box,"Chat model",value.optString("groq_model"),false);
        EditText gemini=field(box,"Gemini speech key · "+(value.optBoolean("gemini_configured")?"connected":"optional"),"",true);
        EditText speech=field(box,"Speech model",value.optString("gemini_model"),false),voice=field(box,"Voice",value.optString("voice"),false);
        EditText creator=field(box,"Core creator attribution",value.optString("creator"),false);
        EditText character=field(box,"Core character · admin only",value.optString("character"),false);character.setSingleLine(false);character.setMinLines(5);character.setMaxLines(12);
        box.addView(d.button("Save KITTY configuration",true,()->{try{
            JSONObject body=new JSONObject().put("groq_model",model.getText().toString().trim()).put("gemini_model",speech.getText().toString().trim()).put("voice",voice.getText().toString().trim()).put("creator",creator.getText().toString()).put("character",character.getText().toString());
            if(groq.length()>0)body.put("groq_key",groq.getText().toString());if(gemini.length()>0)body.put("gemini_key",gemini.getText().toString());
            api("/v1/admin/settings",body,r->{groq.setText("");gemini.setText("");prefs.p.edit().putBoolean("speech_ready",r.optBoolean("gemini_configured")).apply();message("Configuration saved. Test a chat before sharing the app.");});
        }catch(Exception e){message("Check your fields.");}}));
        box.addView(d.button("Available Groq models",false,()->api("/v1/admin/models",null,r->message(r.getJSONArray("models").toString(2)))));
        box.addView(d.button("Remove provider keys",false,()->new AlertDialog.Builder(a).setMessage("Disconnect both saved API keys?").setNegativeButton("Cancel",null).setPositiveButton("Disconnect",(dialog,w)->{try{api("/v1/admin/settings",new JSONObject().put("groq_key","").put("gemini_key",""),r->message("Provider keys disconnected."));}catch(Exception ignored){}}).show()));
        d.gap(box,22);box.addView(d.label("ANNOUNCEMENTS"));d.gap(box,8);
        EditText title=field(box,"Notice title","",false),body=field(box,"Message to every user","",false);body.setSingleLine(false);body.setMinLines(3);
        box.addView(d.button("Publish to user inboxes",true,()->new AlertDialog.Builder(a).setTitle("Publish this notice?").setMessage(title.getText()+"\n\n"+body.getText()).setNegativeButton("Cancel",null).setPositiveButton("Publish",(dialog,w)->{try{api("/v1/admin/notice",new JSONObject().put("title",title.getText().toString()).put("body",body.getText().toString()),r->{title.setText("");body.setText("");message("Notice published. Users see it when opening Inbox. Background notifications are disabled in this permission-light release.");});}catch(Exception ignored){}}).show()));
        d.gap(box,20);box.addView(d.label("OPTIONAL BACKUPS"));d.gap(box,8);box.addView(d.text("Drive: "+(value.optBoolean("drive_connected")?"connected":"not connected")+". Only consenting users are backed up.",13,Design.MUTED));
        box.addView(d.button("Connect admin Drive",false,()->api("/v1/admin/drive",new JSONObject(),r->open(r.getString("url")))));
        box.addView(d.button("Export reviewed training",false,()->a.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"KITTY-reviewed-training.jsonl"),91)));
        box.addView(d.button("Release & setup guide",false,()->open("https://github.com/psychspy7/KITTY.AI/blob/main/docs/START_HERE_V05.md")));
        show("Admin console",box);
    });}
    void saveTraining(Uri destination){
        if(!prefs.admin())return;BrainClient.Session account=new BrainClient.Session(prefs);
        network.execute(()->{try{
            JSONArray rows=BrainClient.request(account,"/v1/admin/export",null).getJSONArray("examples");if(!account.current(prefs))return;
            try(java.io.OutputStream out=a.getContentResolver().openOutputStream(destination,"wt")){if(out==null)throw new java.io.IOException();for(int i=0;i<rows.length();i++)out.write((rows.getJSONObject(i).toString()+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            main.post(()->{if(account.current(prefs))message("Exported "+rows.length()+" reviewed examples. Review these before any separate training job.");});
        }catch(Exception e){main.post(()->{if(account.current(prefs))message("Could not save the training export.");});}});
    }
    private void open(String url){try{a.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException e){message("Install a browser to open this link.");}}
    void accountChanged(){for(AlertDialog dialog:dialogs)dialog.dismiss();dialogs.clear();}
    void close(){accountChanged();closed=true;network.shutdownNow();main.removeCallbacksAndMessages(null);}
}
