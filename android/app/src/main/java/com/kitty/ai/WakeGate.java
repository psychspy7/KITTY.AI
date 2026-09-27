package com.kitty.ai;

/** Final utterances alone authorize a command; partial transcripts are diagnostic. */
final class WakeGate {
    private long armedUntil;private String last="";private long lastAt;
    void arm(long now){armedUntil=now+10000;}
    void clear(){armedUntil=0;}
    String accept(String text,long now){
        text=text.trim();if(text.isEmpty())return null;
        if(text.equals(last)&&now-lastAt<1000)return null;
        last=text;lastAt=now;
        boolean wake=Router.hasWakePhrase(text);
        if(!wake&&now>=armedUntil)return null;
        String command=wake?Router.stripWake(text):text;
        if(command.isEmpty())arm(now);else clear();return command;
    }
    boolean armed(long now){return now<armedUntil;}
}
