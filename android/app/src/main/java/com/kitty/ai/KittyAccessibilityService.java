package com.kitty.ai;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.os.Bundle;
import android.graphics.Rect;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;

public class KittyAccessibilityService extends AccessibilityService {
    public static volatile KittyAccessibilityService instance;
    @Override public void onServiceConnected(){instance=this;}
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { /* No screen logging or background scraping. */ }
    @Override public void onInterrupt(){}
    @Override public void onDestroy(){if(instance==this)instance=null;super.onDestroy();}

    public String execute(Action action){
        if(action.kind.equals("navigation")) {
            int key=action.target.equals("back")?GLOBAL_ACTION_BACK:action.target.equals("recents")?GLOBAL_ACTION_RECENTS:action.target.equals("lock")?GLOBAL_ACTION_LOCK_SCREEN:GLOBAL_ACTION_HOME;
            return performGlobalAction(key)?"Sir, navigation requested.":"Sir, Android couldn't perform that navigation action.";
        }
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null)return "Sir, the current screen isn't accessible. Unlock the phone and try again.";
        List<AccessibilityNodeInfo> all=new ArrayList<>();
        collect(root,all,0);
        try {
            if(action.kind.equals("select_result")){
                if(!"com.google.android.youtube".contentEquals(root.getPackageName()==null?"":root.getPackageName()))return "Sir, open YouTube search results first, then say ‘Hey Kitty, play the first video’.";
                List<AccessibilityNodeInfo> targets=new ArrayList<>();
                for(AccessibilityNodeInfo node:all){
                    if(!node.isVisibleToUser()||!node.isEnabled()||!node.isClickable())continue;
                    String desc=node.getContentDescription()==null?"":node.getContentDescription().toString();
                    String id=node.getViewIdResourceName()==null?"":node.getViewIdResourceName();
                    Rect bounds=new Rect();node.getBoundsInScreen(bounds);
                    if(ScreenTargets.isVideo(desc,id,bounds.width(),bounds.height()))targets.add(node);
                }
                targets.sort(Comparator.comparingInt((AccessibilityNodeInfo n)->{Rect r=new Rect();n.getBoundsInScreen(r);return r.top;}).thenComparingInt(n->{Rect r=new Rect();n.getBoundsInScreen(r);return r.left;}));
                List<AccessibilityNodeInfo> unique=new ArrayList<>();Rect last=null;
                for(AccessibilityNodeInfo node:targets){Rect r=new Rect();node.getBoundsInScreen(r);if(last==null||!Rect.intersects(last,r)){unique.add(node);last=r;}}
                int index=Integer.parseInt(action.target)-1;
                if(index<0||index>=unique.size())return "Sir, I can't identify that numbered video on this screen. Scroll the results into view, or say ‘tap’ followed by its exact visible title.";
                return unique.get(index).performAction(AccessibilityNodeInfo.ACTION_CLICK)?"Sir, selected visible video "+action.target+". Check YouTube for playback.":"Sir, YouTube didn't accept that tap.";
            }
            if(action.kind.equals("tap")) {
                List<AccessibilityNodeInfo> targets=new ArrayList<>();
                for(AccessibilityNodeInfo node:all){
                    if(!node.isVisibleToUser()||!node.isEnabled())continue;
                    String text=node.getText()==null?"":node.getText().toString().trim();
                    String desc=node.getContentDescription()==null?"":node.getContentDescription().toString().trim();
                    if(text.equalsIgnoreCase(action.target)||desc.equalsIgnoreCase(action.target)){
                        AccessibilityNodeInfo click=node;
                        for(int i=0;i<4 && click!=null && !click.isClickable();i++)click=click.getParent();
                        if(click!=null && click.isClickable() && !targets.contains(click))targets.add(click);
                    }
                }
                if(targets.size()!=1)return targets.isEmpty()?"Sir, I can't find a visible button with that exact label.":"Sir, several controls have that label. Please tap the one you mean.";
                boolean ok=targets.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                return ok?"Sir, tap requested. Check the app for completion.":"Sir, that control didn't accept the tap.";
            }
            if(action.kind.equals("type")){
                AccessibilityNodeInfo field=root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                if(field==null||!field.isEditable()){
                    List<AccessibilityNodeInfo> edits=new ArrayList<>();
                    for(AccessibilityNodeInfo n:all)if(n.isEditable()&&n.isVisibleToUser()&&n.isEnabled())edits.add(n);
                    if(edits.size()!=1)return "Sir, focus the text box first.";
                    field=edits.get(0);
                }
                Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,action.text);
                return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args)?"Sir, text entered.":"Sir, this text field didn't allow that action.";
            }
            if(action.kind.equals("scroll")){
                for(AccessibilityNodeInfo n:all)if(n.isScrollable()&&n.isVisibleToUser()){
                    if(n.performAction(action.target.equals("up")?AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD:AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))return "Sir, scrolled.";
                }
                return "Sir, there is no further accessible content to scroll.";
            }
            return "Sir, that screen command is not implemented yet.";
        } finally { for(AccessibilityNodeInfo n:all)n.recycle(); }
    }
    private void collect(AccessibilityNodeInfo node,List<AccessibilityNodeInfo> all,int depth){
        if(node==null)return;
        if(depth>35||all.size()>=500){node.recycle();return;}
        all.add(node);
        for(int i=0;i<node.getChildCount();i++)collect(node.getChild(i),all,depth+1);
    }
}
