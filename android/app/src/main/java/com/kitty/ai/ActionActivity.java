package com.kitty.ai;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
/** Private destination for a user-tapped notification; never an exported IPC API. */
public final class ActionActivity extends Activity {
    private boolean sent;private ChatController chat;
    private final Runnable ready=()->runCommand();
    @Override public void onCreate(Bundle saved){super.onCreate(saved);chat=KittyApp.chat(this);TextView text=new TextView(this);text.setText("KITTY · preparing your command…");setContentView(text);chat.observe(ready);}
    private void runCommand(){if(sent||!chat.loaded)return;sent=true;String command=getIntent().getStringExtra("command");if(command==null||!chat.send(this,command,"voice"))complete();}
    void complete(){startActivity(new Intent(this,MainActivity.class));finish();}
    @Override public void onDestroy(){chat.remove(ready);super.onDestroy();}
}
