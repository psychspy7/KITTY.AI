package com.kitty.ai;
import android.content.*;
import com.google.firebase.auth.FirebaseUser;
import org.json.JSONObject;
import java.util.UUID;

/** Device preferences never contain provider keys, editable core prompts or a service URL. */
final class Prefs {
    final SharedPreferences p;
    Prefs(Context c){
        p=c.getSharedPreferences("kitty",Context.MODE_PRIVATE);
        if(!p.getBoolean("cloud_only_v05",false))p.edit().remove("url").remove("token").remove("iv").remove("role").remove("account_id").remove("email").putBoolean("cloud_only_v05",true).putBoolean("speak",false).apply();
    }
    String url(){return BuildConfig.KITTY_SERVER_URL;}
    boolean cloud(){return true;}
    String accountId(){FirebaseUser u=KittyApp.auth()==null?null:KittyApp.auth().getCurrentUser();return u==null?"":u.getUid();}
    boolean signedIn(){return !accountId().isEmpty();}
    boolean admin(){return signedIn()&&accountId().equals(p.getString("verified_uid",""))&&p.getString("role","").equals("admin");}
    void clearRole(){p.edit().remove("role").remove("verified_uid").putBoolean("speech_ready",false).apply();}
    void account(JSONObject value,String uid){if(!uid.equals(accountId()))return;p.edit().putString("verified_uid",uid).putString("role",value.optString("role","user")).apply();}
    boolean speak(){return p.getBoolean("speak_"+accountId(),false);}
    void speak(boolean value){p.edit().putBoolean("speak_"+accountId(),value).apply();}
    String session(){synchronized(Prefs.class){String key="chat_"+accountId(),id=p.getString(key,"");if(id.isEmpty()){id=UUID.randomUUID().toString();p.edit().putString(key,id).apply();}return id;}}
    void newSession(){p.edit().putString("chat_"+accountId(),UUID.randomUUID().toString()).apply();}
}
