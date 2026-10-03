package com.kitty.ai;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.UUID;
import static org.junit.Assert.*;

/** Exercises a real SQLite upgrade without touching the app's own archive. */
@RunWith(AndroidJUnit4.class)
public class AccountStorageTest {
    @Test public void migrationRetainsHistoryAndDuplicateIdsStayInTheirAccount() throws Exception {
        Context base=InstrumentationRegistry.getInstrumentation().getTargetContext();
        File file=new File(base.getCacheDir(),"qa-storage-"+UUID.randomUUID()+".db");
        Context scoped=new ContextWrapper(base){
            @Override public File getDatabasePath(String name){return file;}
            @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory){return SQLiteDatabase.openOrCreateDatabase(file,factory);}
            @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory,DatabaseErrorHandler handler){return SQLiteDatabase.openOrCreateDatabase(file.getPath(),factory,handler);}
        };
        try(SQLiteDatabase old=SQLiteDatabase.openOrCreateDatabase(file,null)){
            old.execSQL("CREATE TABLE turns(id TEXT PRIMARY KEY,session TEXT,input TEXT,reply TEXT,mode TEXT,source TEXT,created INTEGER,synced INTEGER DEFAULT 0,metadata TEXT DEFAULT '{}',pairing TEXT NOT NULL DEFAULT 'unpaired')");
            old.execSQL("CREATE TABLE feedback(id TEXT PRIMARY KEY,rating INTEGER,correction TEXT)");
            old.execSQL("CREATE TABLE memories(pairing TEXT,id TEXT,text TEXT,dirty INTEGER DEFAULT 0,deleted INTEGER DEFAULT 0,PRIMARY KEY(pairing,id))");
            old.execSQL("INSERT INTO turns(id,session,input,reply,mode,source,created,pairing) VALUES('shared-id','s','A question','A reply','model','chat',1,'account-a')");
            old.execSQL("INSERT INTO feedback VALUES('shared-id',1,'')");old.setVersion(5);
        }
        try(ConversationStore store=new ConversationStore(scoped)){
            assertEquals("A reply",store.recent(null,"account-a",true).get(0).getString("reply"));
            JSONObject b=new JSONObject().put("id","shared-id").put("session","s").put("input","B question").put("reply","B reply").put("mode","model").put("source","chat").put("created",2).put("pairing","account-b");store.save(b);
            assertEquals("A reply",store.recent(null,"account-a",true).get(0).getString("reply"));
            assertEquals("B reply",store.recent(null,"account-b",true).get(0).getString("reply"));
            store.feedback("account-b","shared-id",-1,"B correction");
            assertEquals(1,store.feedbackPending("account-a").get(0).getInt("rating"));
            assertEquals(-1,store.feedbackPending("account-b").get(0).getInt("rating"));
            store.synced("account-a",new JSONArray().put("shared-id"));
            assertEquals(0,store.pending("account-a").length());assertEquals(1,store.pending("account-b").length());
            store.feedbackSynced("account-a",store.feedbackPending("account-a").get(0));
            assertEquals(0,store.feedbackPending("account-a").size());assertEquals(1,store.feedbackPending("account-b").size());
        }finally{SQLiteDatabase.deleteDatabase(file);}
    }
}
