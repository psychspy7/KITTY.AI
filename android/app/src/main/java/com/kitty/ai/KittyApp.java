package com.kitty.ai;
import android.app.Application;
import android.content.Context;
public final class KittyApp extends Application {
    private ChatController chat;
    ShizukuControl shizuku;
    @Override public void onCreate(){super.onCreate();chat=new ChatController(this);shizuku=new ShizukuControl(this);Prefs p=new Prefs(this);if(p.cloud()&&!p.accountId().isEmpty())NoticeWorker.schedule(this);}
    static ChatController chat(Context c){return ((KittyApp)c.getApplicationContext()).chat;}
}
