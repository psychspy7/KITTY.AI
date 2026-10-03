package com.kitty.ai;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.EditText;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

/** Visual layout fixtures only: does not bypass Firebase in the shipped app. */
@RunWith(AndroidJUnit4.class)
public class PremiumLayoutTest {
    private void invoke(MainActivity a,String name) throws Exception {Method m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(a);}
    private Object get(MainActivity a,String name) throws Exception {Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);}
    private void screenshot(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Thread.sleep(250); // Allow the rendered surface to present the new frame.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(bitmap);
        File dir=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir(),"qa-layout");assertTrue(dir.exists()||dir.mkdirs());
        try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
    }
    @Test public void nativeHomeConversationAndComposerFit() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            java.util.concurrent.atomic.AtomicBoolean loaded=new java.util.concurrent.atomic.AtomicBoolean();
            for(int i=0;i<30&&!loaded.get();i++){scenario.onActivity(a->loaded.set(KittyApp.chat(a).loaded));Thread.sleep(100);}
            assertTrue(loaded.get());
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            // Same production renderer, supplied with test-only local display data.
            scenario.onActivity(a->{try{KittyApp.chat(a).turns.clear();invoke(a,"home");invoke(a,"renderConversation");}catch(Exception e){throw new AssertionError(e);}});
            screenshot("home-fixture");
            scenario.onActivity(a->{try{
                ChatController chat=KittyApp.chat(a);JSONObject turn=new JSONObject().put("id","visual-fixture").put("input","Who made you?").put("reply","Sir, I’m KITTY AI, made by Virat with the help of Kitty Corp. Your helpful friend with a mischievous streak.").put("mode","identity");chat.turns.add(turn);invoke(a,"renderConversation");
            }catch(Exception e){throw new AssertionError(e);}});
            screenshot("conversation-fixture");
            scenario.onActivity(a->{try{EditText input=(EditText)get(a,"input");input.requestFocus();input.setText("Let’s make something great");View send=(View)get(a,"send");assertTrue(send.getWidth()>0);assertTrue(input.getWidth()>100);assertTrue(send.getRight()<=((View)send.getParent()).getWidth());}catch(Exception e){throw new AssertionError(e);}});
            screenshot("composer-fixture");
        }
    }
}
