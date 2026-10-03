package com.kitty.ai;
import android.app.Application;
import android.content.Context;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;

public final class KittyApp extends Application {
    private static FirebaseAuth auth;
    private ChatController chat;
    static boolean configured(){return !BuildConfig.FIREBASE_APP_ID.isEmpty()&&!BuildConfig.FIREBASE_API_KEY.isEmpty()&&!BuildConfig.FIREBASE_PROJECT_ID.isEmpty()&&!BuildConfig.GOOGLE_WEB_CLIENT_ID.isEmpty()&&!BuildConfig.KITTY_SERVER_URL.isEmpty();}
    @Override public void onCreate(){
        super.onCreate();
        if(configured()){
            try{
                FirebaseOptions options=new FirebaseOptions.Builder().setApplicationId(BuildConfig.FIREBASE_APP_ID).setApiKey(BuildConfig.FIREBASE_API_KEY).setProjectId(BuildConfig.FIREBASE_PROJECT_ID).build();
                FirebaseApp app=FirebaseApp.getApps(this).isEmpty()?FirebaseApp.initializeApp(this,options):FirebaseApp.getInstance();
                auth=FirebaseAuth.getInstance(app);
            }catch(IllegalArgumentException ignored){auth=null;}
        }
        chat=new ChatController(this);
    }
    static FirebaseAuth auth(){return auth;}
    static ChatController chat(Context c){return ((KittyApp)c.getApplicationContext()).chat;}
}
