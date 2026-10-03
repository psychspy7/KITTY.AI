package com.kitty.ai;

import android.content.Context;
import android.media.*;
import android.os.*;
import android.speech.tts.*;
import java.util.*;

/** One audio owner shared by typed chat, voice commands and voice previews. */
final class SpeechOutput {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Prefs prefs;private final AudioManager audio;private final AudioFocusRequest focus;
    String issue="";
    private TextToSpeech tts;private boolean ready,failed,open,active,cloudPreferred;private int generation;
    private final CloudVoice cloud;
    private final ArrayDeque<String> waiting=new ArrayDeque<>();private final Set<String> utterances=new HashSet<>();
    Runnable changed=()->{};
    SpeechOutput(Context c){
        prefs=new Prefs(c);cloud=new CloudVoice(c);audio=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);
        AudioAttributes attrs=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs).setOnAudioFocusChangeListener(value->{if(value<0)stop();},main).build();
        tts=new TextToSpeech(c,code->main.post(()->{ready=code==TextToSpeech.SUCCESS;failed=!ready;if(ready){ready=tts.setLanguage(Locale.forLanguageTag("en-IN"))>=TextToSpeech.LANG_AVAILABLE;failed=!ready;tts.setAudioAttributes(attrs);}if(ready){while(!waiting.isEmpty())enqueue(waiting.removeFirst());}else{waiting.clear();issue="Install an offline TTS voice in Android settings";}finish();changed.run();}));
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){public void onStart(String id){}public void onDone(String id){main.post(()->complete(id));}public void onError(String id){onDone(id);}public void onStop(String id,boolean interrupted){onDone(id);}});
    }
    boolean active(){return active;}
    private void active(boolean value){if(active!=value){active=value;changed.run();}}
    void begin(){stop();open=true;}
    void beginCloud(){begin();cloudPreferred=prefs.cloud()&&prefs.p.getBoolean("speech_ready",false)&&prefs.p.getBoolean("cloud_voice",true);}
    void enqueue(String text){
        text=text.replaceAll("[*#`]","").trim();if(text.isEmpty())return;
        if(cloudPreferred){
            if(!active&&audio.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){issue="Audio focus unavailable; open KITTY to hear replies";changed.run();return;}
            active(true);int own=generation;
            for(int start=0;start<text.length();start+=850){String chunk=text.substring(start,Math.min(text.length(),start+850));String id="cloud-"+UUID.randomUUID();utterances.add(id);cloud.enqueue(chunk,ok->{if(own!=generation)return;if(!ok){cloudPreferred=false;enqueue(chunk);issue="Cloud voice unavailable; using the phone voice";changed.run();}complete(id);});}
            return;
        }
        if(failed)return;
        if(!ready){waiting.add(text);int own=generation;main.postDelayed(()->{if(own==generation&&!ready){waiting.clear();failed=true;issue="Speech engine did not start; check Android TTS settings";finish();changed.run();}},5000);return;}
        if(!active&&audio.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){issue="Audio focus unavailable; open KITTY to hear replies";changed.run();return;}
        issue="";
        active(true);String id=generation+"-"+UUID.randomUUID();utterances.add(id);
        if(tts.speak(text.substring(0,Math.min(3500,text.length())),TextToSpeech.QUEUE_ADD,null,id)==TextToSpeech.ERROR)complete(id);
        int own=generation;
        main.postDelayed(()->{if(own==generation&&utterances.contains(id)){issue="Speech engine stalled; try read aloud again";stop();changed.run();}},120000);
    }
    private void complete(String id){utterances.remove(id);finish();}
    void end(){open=false;finish();}
    private void finish(){if(!open&&waiting.isEmpty()&&utterances.isEmpty()){audio.abandonAudioFocusRequest(focus);active(false);}}
    void say(String text){begin();enqueue(text);end();}
    void stop(){generation++;open=false;cloudPreferred=false;cloud.stop();waiting.clear();utterances.clear();if(tts!=null)tts.stop();audio.abandonAudioFocusRequest(focus);active(false);}
    List<Voice> voices(){List<Voice> out=new ArrayList<>();if(ready&&tts.getVoices()!=null)for(Voice v:tts.getVoices())if(!v.isNetworkConnectionRequired()&&(v.getLocale().getLanguage().equals("en")||v.getLocale().getLanguage().equals("hi")))out.add(v);out.sort(Comparator.comparing(Voice::getName));return out;}
    void preview(Voice voice){prefs.p.edit().putString("voice",voice.getName()).apply();tts.setVoice(voice);ready=true;failed=false;issue="";say("At your service, Sir. Try not to make this a full time rescue operation.");}
}
