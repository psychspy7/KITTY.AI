package com.kitty.ai;

import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import java.util.Locale;

final class Speech {
    static boolean configure(TextToSpeech tts,Prefs prefs){
        tts.setLanguage(Locale.forLanguageTag("en-IN"));tts.setSpeechRate(1.02f);
        String selected=prefs.p.getString("voice","");Voice fallback=null;
        if(tts.getVoices()!=null)for(Voice v:tts.getVoices()){
            if(v.isNetworkConnectionRequired())continue;
            if(v.getName().equals(selected)){return tts.setVoice(v)==TextToSpeech.SUCCESS;}
            if(v.getLocale().getLanguage().equals("en") && (fallback==null||v.getLocale().getCountry().equals("IN")))fallback=v;
        }
        return fallback!=null&&tts.setVoice(fallback)==TextToSpeech.SUCCESS;
    }
}
