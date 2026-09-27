package com.kitty.ai;
import org.junit.Test;
import static org.junit.Assert.*;
public class WakeGateTest {
    @Test public void ordinarySpeechCannotRunActions(){WakeGate g=new WakeGate();assertNull(g.accept("open settings",100));assertNull(g.accept("cutie call dad",2000));}
    @Test public void sameUtteranceAndTwoPartWakeBothWork(){WakeGate g=new WakeGate();assertEquals("battery",g.accept("hey kitty battery",100));assertEquals("",g.accept("hey kitty",2000));assertEquals("open settings",g.accept("open settings",8000));assertNull(g.accept("call dad",9000));}
    @Test public void tapWindowExpires(){WakeGate g=new WakeGate();g.arm(100);assertEquals("battery",g.accept("battery",101));g.arm(2000);assertNull(g.accept("call dad",12001));}
    @Test public void duplicateFinalResultDoesNotExecuteTwice(){WakeGate g=new WakeGate();assertEquals("battery",g.accept("hey kitty battery",5000));assertNull(g.accept("hey kitty battery",5500));assertEquals("battery",g.accept("hey kitty battery",7000));}
}
