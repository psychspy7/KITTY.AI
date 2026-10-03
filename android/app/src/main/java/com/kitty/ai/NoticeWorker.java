package com.kitty.ai;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.work.*;
import org.json.*;
import java.util.concurrent.TimeUnit;

/** Durable notice fetch. Android may delay background work; no hidden microphone service. */
public final class NoticeWorker extends Worker {
    public NoticeWorker(Context c,WorkerParameters p){super(c,p);}
    static void schedule(Context c){Constraints constraints=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();WorkManager.getInstance(c).enqueueUniquePeriodicWork("kitty-notices",ExistingPeriodicWorkPolicy.KEEP,new PeriodicWorkRequest.Builder(NoticeWorker.class,15,TimeUnit.MINUTES).setConstraints(constraints).build());}
    static void cancel(Context c){WorkManager.getInstance(c).cancelUniqueWork("kitty-notices");}
    @Override public Result doWork(){try{fetch(getApplicationContext());return Result.success();}catch(Exception e){return Result.retry();}}
    static synchronized void fetch(Context c) throws Exception {
        Prefs p=new Prefs(c);if(!p.cloud()||p.accountId().isEmpty()||p.token().isEmpty())return;
        String identity=p.url()+"|"+p.accountId();BrainClient.Session account=new BrainClient.Session(p);JSONArray list=BrainClient.request(account,"/v1/notices",null).getJSONArray("notices");
        // Account may have switched while the request was on the wire.
        if(!account.current(p)||!identity.equals(p.url()+"|"+p.accountId()))return;
        NotificationManager nm=c.getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("notices","KITTY news and updates",NotificationManager.IMPORTANCE_DEFAULT));
        if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        if(!nm.areNotificationsEnabled())return;
        String seenKey="notice_seen_"+account.pairing;int seen=p.p.getInt(seenKey,0),latest=seen;
        for(int i=list.length()-1;i>=0;i--){JSONObject n=list.getJSONObject(i);int id=n.getInt("id");if(id<=seen)continue;PendingIntent open=PendingIntent.getActivity(c,1000+id,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);nm.notify(1000+id,new Notification.Builder(c,"notices").setSmallIcon(R.drawable.ic_kitty).setContentTitle(n.optString("title")).setContentText(n.optString("body")).setStyle(new Notification.BigTextStyle().bigText(n.optString("body"))).setContentIntent(open).setAutoCancel(true).build());latest=Math.max(latest,id);}
        p.p.edit().putInt(seenKey,latest).apply();
    }
}
