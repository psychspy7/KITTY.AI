package com.kitty.ai;
import java.util.concurrent.TimeUnit;
/** Runs with the Shizuku identity only after the owner authorizes KITTY. */
public final class InputUserService extends IInputService.Stub {
    public InputUserService(){}
    @Override public boolean execute(String kind,String target){
        String[] args=InputCommands.arguments(kind,target);if(args==null)return false;
        Process p=null;try{p=new ProcessBuilder(args).redirectErrorStream(true).start();if(!p.waitFor(3,TimeUnit.SECONDS)){p.destroyForcibly();return false;}return p.exitValue()==0;}catch(Exception e){if(p!=null)p.destroyForcibly();return false;}
    }
    @Override public void destroy(){System.exit(0);}
}
