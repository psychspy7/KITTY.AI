package com.kitty.ai;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Durable local archive and outbox. Call only on the controller's disk executor. */
final class ConversationStore extends SQLiteOpenHelper {
    private final Prefs prefs;
    ConversationStore(Context c){super(c,"conversations.db",null,4);prefs=new Prefs(c);setWriteAheadLoggingEnabled(true);}
    String pairing(){
        String token=prefs.token();if(token.isEmpty())return "unpaired";
        try{MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] bytes=digest.digest((prefs.url()+"\n"+token).getBytes(StandardCharsets.UTF_8));StringBuilder id=new StringBuilder();for(byte b:bytes)id.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return id.toString();}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE turns(id TEXT PRIMARY KEY,session TEXT,input TEXT,reply TEXT,mode TEXT,source TEXT,created INTEGER,synced INTEGER DEFAULT 0,metadata TEXT DEFAULT '{}',pairing TEXT NOT NULL DEFAULT 'unpaired')");feedbackTable(db);}
    private static void feedbackTable(SQLiteDatabase db){db.execSQL("CREATE TABLE IF NOT EXISTS feedback(id TEXT PRIMARY KEY,rating INTEGER,correction TEXT)");}
    @Override public void onUpgrade(SQLiteDatabase db,int old,int next){if(old<2)feedbackTable(db);if(old<3)db.execSQL("ALTER TABLE turns ADD COLUMN metadata TEXT DEFAULT '{}'");if(old<4){db.execSQL("ALTER TABLE turns ADD COLUMN pairing TEXT NOT NULL DEFAULT 'unpaired'");ContentValues v=new ContentValues();v.put("pairing",pairing());db.update("turns",v,null,null);}}
    void feedback(String id,int rating,String correction){ContentValues v=new ContentValues();v.put("id",id);v.put("rating",rating);v.put("correction",correction);getWritableDatabase().insertWithOnConflict("feedback",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    List<JSONObject> feedbackPending(){List<JSONObject> rows=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT f.id AS response_id,f.rating,f.correction FROM feedback f JOIN turns t ON f.id=t.id WHERE t.pairing=? LIMIT 20",new String[]{pairing()})){while(c.moveToNext())rows.add(new JSONObject().put("response_id",c.getString(0)).put("rating",c.getInt(1)).put("correction",c.getString(2)));}catch(JSONException e){throw new IllegalStateException(e);}return rows;}
    void feedbackSynced(JSONObject f){getWritableDatabase().delete("feedback","id=? AND rating=? AND correction=?",new String[]{f.optString("response_id"),f.optString("rating"),f.optString("correction")});}
    void recover(){getWritableDatabase().execSQL("UPDATE turns SET mode='interrupted',reply='Sir, this request was interrupted. Please retry.',synced=0 WHERE mode='pending'");}
    void save(JSONObject t){ContentValues v=new ContentValues();for(String key:new String[]{"id","session","input","reply","mode","source","metadata"})v.put(key,t.optString(key));v.put("pairing",t.optString("pairing",pairing()));v.put("created",t.optLong("created"));v.put("synced",0);getWritableDatabase().insertWithOnConflict("turns",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    private JSONObject row(Cursor c) throws JSONException {JSONObject t=new JSONObject();for(int i=0;i<c.getColumnCount();i++)if(c.getColumnName(i).equals("created"))t.put("created",c.getLong(i));else t.put(c.getColumnName(i),c.getString(i));return t;}
    List<JSONObject> recent(String session){List<JSONObject> rows=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM turns "+(session==null?"":"WHERE session=? ")+"ORDER BY created DESC LIMIT 100",session==null?null:new String[]{session})){while(c.moveToNext())rows.add(row(c));}catch(JSONException e){throw new IllegalStateException(e);}Collections.reverse(rows);return rows;}
    JSONArray pending(){JSONArray rows=new JSONArray();int bytes=0;try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM turns WHERE synced=0 AND mode!='pending' AND pairing=? ORDER BY created LIMIT 20",new String[]{pairing()})){while(c.moveToNext()){JSONObject t=row(c);bytes+=t.toString().getBytes(StandardCharsets.UTF_8).length;if(bytes>200000)break;rows.put(t);}}catch(JSONException e){throw new IllegalStateException(e);}return rows;}
    void synced(JSONArray ids){for(int i=0;i<ids.length();i++){ContentValues v=new ContentValues();v.put("synced",1);getWritableDatabase().update("turns",v,"id=?",new String[]{ids.optString(i)});}}
}
