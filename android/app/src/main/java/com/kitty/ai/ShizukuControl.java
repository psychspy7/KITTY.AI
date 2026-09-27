package com.kitty.ai;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import rikka.shizuku.Shizuku;
import java.util.concurrent.*;
import java.util.function.Consumer;
/** Optional backend. Accessibility remains available for semantic labels and Unicode typing. */
final class ShizukuControl {
    private final Context app;private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private volatile IInputService service;private boolean binding;
    private final ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){service=IInputService.Stub.asInterface(b);binding=false;KittyApp.chat(app).note("Shizuku ready for navigation and coordinate taps, Sir.");}public void onServiceDisconnected(ComponentName n){service=null;binding=false;}};
    ShizukuControl(Context c){app=c;Shizuku.addRequestPermissionResultListener((code,result)->{if(code==61&&result==PackageManager.PERMISSION_GRANTED)connect();});Shizuku.addBinderDeadListener(()->{service=null;binding=false;});Shizuku.addBinderReceivedListenerSticky(()->{if(new Prefs(app).p.getBoolean("shizuku",false))connect();});}
    void enable(){
        try{if(!Shizuku.pingBinder()){KittyApp.chat(app).note("Install and start Shizuku, then tap Connect Shizuku again, Sir.");return;}
            new Prefs(app).p.edit().putBoolean("shizuku",true).apply();if(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED)Shizuku.requestPermission(61);else connect();
        }catch(RuntimeException e){KittyApp.chat(app).note("Shizuku could not connect. Check its app, Sir.");}
    }
    private void connect(){try{if(binding||service!=null||!Shizuku.pingBinder()||Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED)return;binding=true;
        Shizuku.bindUserService(new Shizuku.UserServiceArgs(new ComponentName(app,InputUserService.class)).daemon(false).processNameSuffix("input").debuggable(false).version(20),connection);
        main.postDelayed(()->{if(service==null)binding=false;},8000);
    }catch(RuntimeException e){binding=false;}}
    boolean tryExecute(Action action,Consumer<String> reply){
        IInputService current=service;if(current==null||InputCommands.arguments(action.kind,action.target)==null)return false;
        worker.execute(()->{String result;try{result=current.execute(action.kind,action.target)?"Sir, Shizuku accepted the screen command.":"Sir, Shizuku could not complete that command.";}catch(Exception e){service=null;result="Sir, Shizuku disconnected. Reconnect it in Settings.";}String message=result;main.post(()->reply.accept(message));});return true;
    }
}
