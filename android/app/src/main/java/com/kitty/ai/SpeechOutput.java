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
    private TextToSpeech tts;private boolean ready,failed,open,active;private int generation;
    private final ArrayDeque<String> waiting=new ArrayDeque<>();private final Set<String> utterances=new HashSet<>();
    Runnable changed=()->{};
    SpeechOutput(Context c){
        prefs=new Prefs(c);audio=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);
        AudioAttributes attrs=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs).setOnAudioFocusChangeListener(value->{if(value<0)stop();},main).build();
        tts=new TextToSpeech(c,code->main.post(()->{ready=code==TextToSpeech.SUCCESS;failed=!ready;if(ready){ready=Speech.configure(tts,prefs);failed=!ready;tts.setAudioAttributes(attrs);}if(ready){while(!waiting.isEmpty())enqueue(waiting.removeFirst());}else{waiting.clear();issue="Install an offline TTS voice in Android settings";}finish();changed.run();}));
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){public void onStart(String id){}public void onDone(String id){main.post(()->complete(id));}public void onError(String id){onDone(id);}public void onStop(String id,boolean interrupted){onDone(id);}});
    }
    boolean active(){return active;}
    private void active(boolean value){if(active!=value){active=value;changed.run();}}
    void begin(){stop();open=true;}
    void enqueue(String text){
        text=text.replaceAll("[*#`]","").trim();if(text.isEmpty()||!prefs.speak()||failed)return;
        if(!ready){waiting.add(text);int own=generation;main.postDelayed(()->{if(own==generation&&!ready){waiting.clear();failed=true;issue="Speech engine did not start; check Android TTS settings";finish();changed.run();}},5000);return;}
        if(!active&&audio.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){issue="Audio focus unavailable; open KITTY to hear replies";changed.run();return;}
        issue="";
        active(true);String id=generation+"-"+UUID.randomUUID();utterances.add(id);
        if(tts.speak(text.substring(0,Math.min(3500,text.length())),TextToSpeech.QUEUE_ADD,null,id)==TextToSpeech.ERROR)complete(id);
    }
    private void complete(String id){utterances.remove(id);finish();}
    void end(){open=false;finish();}
    private void finish(){if(!open&&waiting.isEmpty()&&utterances.isEmpty()){audio.abandonAudioFocusRequest(focus);active(false);}}
    void say(String text){begin();enqueue(text);end();}
    void stop(){generation++;open=false;waiting.clear();utterances.clear();if(tts!=null)tts.stop();audio.abandonAudioFocusRequest(focus);active(false);}
    List<Voice> voices(){List<Voice> out=new ArrayList<>();if(ready&&tts.getVoices()!=null)for(Voice v:tts.getVoices())if(!v.isNetworkConnectionRequired()&&(v.getLocale().getLanguage().equals("en")||v.getLocale().getLanguage().equals("hi")))out.add(v);out.sort(Comparator.comparing(Voice::getName));return out;}
    void preview(Voice voice){prefs.p.edit().putString("voice",voice.getName()).apply();tts.setVoice(voice);ready=true;failed=false;issue="";say("At your service, Sir. Try not to make this a full time rescue operation.");}
}
