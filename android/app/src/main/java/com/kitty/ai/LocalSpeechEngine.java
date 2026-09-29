package com.kitty.ai;

import android.annotation.SuppressLint;
import android.media.*;
import android.os.*;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/** One recorder, local Vosk decoding, level meter and Android-silencing status. */
final class LocalSpeechEngine {
    private final Recognizer recognizer;
    private final AudioRecord recorder;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final BiConsumer<Float,Boolean> meter;
    private final AtomicBoolean reset=new AtomicBoolean();
    private volatile boolean running,paused;
    private Thread thread;
    private final AudioManager.AudioRecordingCallback recordingCallback=new AudioManager.AudioRecordingCallback(){
        @Override public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configs){report(0);}
    };
    @SuppressLint("MissingPermission")
    LocalSpeechEngine(Recognizer recognizer,BiConsumer<Float,Boolean> meter){
        this.recognizer=recognizer;this.meter=meter;
        int bytes=Math.max(12800,AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT));
        recorder=new AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION).setAudioFormat(new AudioFormat.Builder().setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()).setBufferSizeInBytes(bytes).build();
        recorder.registerAudioRecordingCallback(command->main.post(command),recordingCallback);
    }
    boolean startListening(RecognitionListener listener){
        if(running||recorder.getState()!=AudioRecord.STATE_INITIALIZED)return false;
        try{recorder.startRecording();}catch(RuntimeException e){return false;}
        if(recorder.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)return false;
        running=true;
        thread=new Thread(()->{
            short[] buffer=new short[3200];long last=0;
            try{
                while(running){
                    int n=recorder.read(buffer,0,buffer.length,AudioRecord.READ_BLOCKING);
                    if(!running)break;
                    if(n<0)throw new IllegalStateException("Audio read failed");
                    if(n==0)continue;
                    if(reset.getAndSet(false))recognizer.reset();
                    double sum=0;for(int i=0;i<n;i++)sum+=(double)buffer[i]*buffer[i];
                    float level=(float)Math.min(1,Math.sqrt(sum/n)/6000);
                    long now=SystemClock.elapsedRealtime();
                    if(now-last>=200){last=now;report(level);}
                    if(paused)continue;
                    if(recognizer.acceptWaveForm(buffer,n))listener.onResult(recognizer.getResult());
                    else listener.onPartialResult(recognizer.getPartialResult());
                }
            }catch(Exception e){if(running)listener.onError(e);}
            finally{running=false;}
        },"kitty-offline-speech");thread.start();return true;
    }
    private void report(float level){
        main.post(()->{if(!running)return;AudioRecordingConfiguration config=recorder.getActiveRecordingConfiguration();meter.accept(level,config!=null&&config.isClientSilenced());});
    }
    void setPause(boolean value){paused=value;reset.set(true);}
    void reset(){reset.set(true);}
    void cancel(){running=false;try{recorder.stop();}catch(IllegalStateException ignored){}}
    void shutdown(){
        cancel();
        if(thread!=null){try{thread.join();}catch(InterruptedException e){Thread.currentThread().interrupt();}}
        recorder.unregisterAudioRecordingCallback(recordingCallback);recorder.release();main.removeCallbacksAndMessages(null);
    }
}
