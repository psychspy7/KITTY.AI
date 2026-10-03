package com.kitty.ai;

import android.content.Context;
import android.media.*;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import okhttp3.*;

/** Ordered, cancellable cloud speech with transient audio files and a phone-voice fallback. */
final class CloudVoice {
    private final Context context;private final Prefs prefs;private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ArrayDeque<Item> queue=new ArrayDeque<>();private volatile Call call;private MediaPlayer player;
    private volatile int generation;private boolean busy;private File playing;
    private static final class Item {final String text;final Consumer<Boolean> done;final BrainClient.Session account;Item(String t,Consumer<Boolean> d,BrainClient.Session a){text=t;done=d;account=a;}}
    CloudVoice(Context c){context=c.getApplicationContext();prefs=new Prefs(c);}
    void enqueue(String text,Consumer<Boolean> done){queue.add(new Item(text,done,new BrainClient.Session(prefs)));next();}
    private void next(){
        if(busy||queue.isEmpty())return;busy=true;Item item=queue.removeFirst();int own=generation;
        worker.execute(()->{File file=null;boolean success=false;try{
            Call request=BrainClient.speechCall(item.account,item.text);call=request;
            if(own!=generation){request.cancel();return;}
            try(Response response=request.execute()){
                if(!response.isSuccessful()||response.body()==null)throw new IOException("Cloud speech unavailable");
                okio.BufferedSource source=response.body().source();source.request(5000001);if(source.getBuffer().size()>5000000)throw new IOException("Speech too large");byte[] raw=source.readByteArray();
                if(raw.length<12||raw[0]!='R'||raw[1]!='I'||raw[2]!='F'||raw[3]!='F')throw new IOException("Invalid audio");
                file=File.createTempFile("kitty-voice-",".wav",context.getCacheDir());try(FileOutputStream out=new FileOutputStream(file)){out.write(raw);}success=true;
            }
        }catch(Exception ignored){}finally{call=null;final File result=file;final boolean ok=success;main.post(()->{if(own!=generation){if(result!=null)result.delete();return;}if(!ok){complete(item,false);return;}play(item,result,own);});}});
    }
    private void play(Item item,File file,int own){
        playing=file;player=new MediaPlayer();player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
        player.setOnCompletionListener(p->complete(item,true));player.setOnErrorListener((p,a,b)->{complete(item,false);return true;});player.setOnPreparedListener(MediaPlayer::start);
        try{player.setDataSource(file.getAbsolutePath());player.prepareAsync();}catch(Exception e){complete(item,false);}
        main.postDelayed(()->{if(own==generation&&busy&&playing==file)complete(item,false);},90000);
    }
    private void complete(Item item,boolean success){if(player!=null){player.release();player=null;}if(playing!=null){playing.delete();playing=null;}busy=false;item.done.accept(success);if(!success)while(!queue.isEmpty())queue.removeFirst().done.accept(false);next();}
    void stop(){generation++;Call c=call;if(c!=null)c.cancel();queue.clear();busy=false;if(player!=null){player.release();player=null;}if(playing!=null){playing.delete();playing=null;}}
}
