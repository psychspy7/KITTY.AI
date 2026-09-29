package com.kitty.ai;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.BatteryManager;
import android.provider.ContactsContract;
import android.provider.Settings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class Actions {
    public static void execute(Context source,Action a,Consumer<String> reply){
        Prefs prefs=new Prefs(source);
        Context launcher=source instanceof Activity?source:KittyAccessibilityService.instance!=null?KittyAccessibilityService.instance:source;
        KeyguardManager km=(KeyguardManager)source.getSystemService(Context.KEYGUARD_SERVICE);
        if(km.isKeyguardLocked()&&!a.kind.equals("battery")){reply.accept("Sir, unlock the phone before I control apps.");return;}
        try {
            switch(a.kind){
                case "battery":
                    int level=((BatteryManager)source.getSystemService(Context.BATTERY_SERVICE)).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                    reply.accept("Sir, your phone is at "+level+" percent. I'm judging the charger, not you.");return;
                case "select_result":case "tap":case "type":case "scroll":case "navigation":
                    if(((KittyApp)source.getApplicationContext()).shizuku.tryExecute(a,reply))return;
                    if(KittyAccessibilityService.instance==null){reply.accept("Sir, enable KITTY's Accessibility service in Settings for screen commands.");return;}
                    reply.accept(KittyAccessibilityService.instance.execute(a));return;
                case "youtube_search":
                    Intent youtube=new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com/results?search_query="+Uri.encode(a.target)));
                    youtube.setPackage("com.google.android.youtube");
                    try {launch(launcher,youtube);}catch(ActivityNotFoundException e){youtube.setPackage(null);launch(launcher,youtube);}
                    reply.accept("Sir, YouTube search opened for "+a.target+". Choose a result, or say Hey Kitty, tap followed by its visible title.");return;
                case "web_search":
                    launch(launcher,new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/search?q="+Uri.encode(a.target))));
                    reply.accept("Sir, web search opened. The internet is now involved; expectations remain negotiable.");return;
                case "open_app":openApp(launcher,a.target,reply);return;
                case "call":case "whatsapp":
                    resolveContact(source,a.target,contact->{
                        try {
                            if(a.kind.equals("call")){
                                boolean direct=prefs.directCalls()&&source.checkSelfPermission(Manifest.permission.CALL_PHONE)==PackageManager.PERMISSION_GRANTED;
                                launch(launcher,new Intent(direct?Intent.ACTION_CALL:Intent.ACTION_DIAL,Uri.fromParts("tel",contact.number,null)));
                                reply.accept(direct?"Sir, call requested for "+contact.name+".":"Sir, the dialer is ready for "+contact.name+". Direct calls can be enabled in Settings.");
                            } else {
                                String number=Router.internationalNumber(contact.number,prefs.country());
                                if(number==null){reply.accept("Sir, save this contact with its full international number, beginning with + and the country code.");return;}
                                Intent whats=new Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/"+number.substring(1)+"?text="+Uri.encode(a.text)));
                                whats.setPackage("com.whatsapp");
                                launch(launcher,whats);
                                reply.accept("Sir, WhatsApp was asked to open the draft for "+contact.name+". Once it appears, say Hey Kitty, tap Send, or tap Send yourself.");
                            }
                        }catch(ActivityNotFoundException e){reply.accept("Sir, the required phone or WhatsApp app isn't installed.");}
                        catch(SecurityException e){reply.accept("Sir, Android denied that action. Check the permission in Settings.");}
                    },reply);return;
                default:reply.accept("Sir, that action hasn't been implemented yet.");
            }
        }catch(ActivityNotFoundException e){reply.accept("Sir, no installed app can open that action.");}
        catch(SecurityException e){reply.accept("Sir, Android denied that action. Open KITTY and check its permissions.");}
        catch(Exception e){reply.accept("Sir, the phone couldn't complete that action. Please try from KITTY's screen.");}
    }
    static void launch(Context c,Intent intent){if(!(c instanceof Activity))intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(intent);}
    private static void openApp(Context c,String target,Consumer<String> reply){
        String name=target.toLowerCase(Locale.ROOT).trim();
        if(name.equals("settings")){launch(c,new Intent(Settings.ACTION_SETTINGS));reply.accept("Sir, Android Settings opened.");return;}
        if(name.equals("utube"))name="youtube";
        Intent query=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> installed=c.getPackageManager().queryIntentActivities(query,0),exact=new ArrayList<>(),partial=new ArrayList<>();
        for(ResolveInfo r:installed){String label=r.loadLabel(c.getPackageManager()).toString().toLowerCase(Locale.ROOT);if(label.equals(name))exact.add(r);else if(label.contains(name))partial.add(r);}
        List<ResolveInfo> choices=exact.isEmpty()?partial:exact;
        if(choices.isEmpty()){reply.accept("Sir, I couldn't find an installed app named "+target+".");return;}
        Consumer<ResolveInfo> go=r->{Intent intent=c.getPackageManager().getLaunchIntentForPackage(r.activityInfo.packageName);if(intent!=null){launch(c,intent);reply.accept("Sir, opening "+r.loadLabel(c.getPackageManager())+".");}else reply.accept("Sir, that app doesn't have a launch screen.");};
        if(choices.size()==1)go.accept(choices.get(0));
        else if(c instanceof Activity){String[] labels=choices.stream().map(r->r.loadLabel(c.getPackageManager()).toString()).toArray(String[]::new);new AlertDialog.Builder(c).setTitle("Which app, Sir?").setItems(labels,(d,i)->go.accept(choices.get(i))).setOnCancelListener(d->reply.accept("Sir, command cancelled.")).setNegativeButton("Cancel",(d,w)->reply.accept("Sir, command cancelled.")).show();}
        else reply.accept("Sir, several apps match that name. Open KITTY and give the command there to choose.");
    }
    static final class Contact {final String name,number;Contact(String name,String number){this.name=name;this.number=number;}}
    private static void resolveContact(Context c,String target,Consumer<Contact> selected,Consumer<String> reply){
        if(target.matches("[+0-9() .-]+")){
            String number=target.replaceAll("[^+0-9]", "");
            if(!number.matches("\\+?\\d{3,15}")){reply.accept("Sir, that phone number looks incomplete.");return;}
            selected.accept(new Contact(number,number));return;
        }
        if(c.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){reply.accept("Sir, enable Contacts permission in KITTY Settings, or use a phone number.");return;}
        LinkedHashMap<String,Contact> exact=new LinkedHashMap<>(),partial=new LinkedHashMap<>();
        String[] projection={ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER};
        try(Cursor cursor=c.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,projection,null,null,null)){
            if(cursor!=null)while(cursor.moveToNext()){
                String name=cursor.getString(0),number=cursor.getString(1);
                if(name==null||number==null)continue;
                Contact item=new Contact(name,number);
                String key=Router.internationalNumber(number,new Prefs(c).country());if(key==null)key=number.replaceAll("[^+0-9]", "");
                if(name.equalsIgnoreCase(target))exact.put(key,item);else if(name.toLowerCase(Locale.ROOT).contains(target.toLowerCase(Locale.ROOT)))partial.put(key,item);
            }
        }
        List<Contact> matches=new ArrayList<>((exact.isEmpty()?partial:exact).values());
        if(matches.isEmpty()){reply.accept("Sir, no contact matched "+target+". Try their full saved name or number.");return;}
        if(matches.size()==1){selected.accept(matches.get(0));return;}
        if(!(c instanceof Activity)){reply.accept("Sir, several numbers match. Open KITTY to choose, or say the full international number.");return;}
        String[] labels=matches.stream().map(x->x.name+"  "+x.number).toArray(String[]::new);
        new AlertDialog.Builder(c).setTitle("Which number, Sir?").setItems(labels,(d,i)->selected.accept(matches.get(i))).setOnCancelListener(d->reply.accept("Sir, command cancelled.")).setNegativeButton("Cancel",(d,w)->reply.accept("Sir, command cancelled.")).show();
    }
}
