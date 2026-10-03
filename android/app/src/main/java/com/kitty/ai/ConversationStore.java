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
    ConversationStore(Context c){super(c,"conversations.db",null,5);prefs=new Prefs(c);setWriteAheadLoggingEnabled(true);}
    String pairing(){return pairing(prefs.url(),prefs.accountId(),prefs.accountId().isEmpty());}
    static String pairing(String url,String identity,boolean empty){
        if(empty)return "unpaired";
        try{MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] bytes=digest.digest((url+"\n"+identity).getBytes(StandardCharsets.UTF_8));StringBuilder id=new StringBuilder();for(byte b:bytes)id.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return id.toString();}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE turns(id TEXT PRIMARY KEY,session TEXT,input TEXT,reply TEXT,mode TEXT,source TEXT,created INTEGER,synced INTEGER DEFAULT 0,metadata TEXT DEFAULT '{}',pairing TEXT NOT NULL DEFAULT 'unpaired')");feedbackTable(db);memoryTable(db);}
    private static void memoryTable(SQLiteDatabase db){db.execSQL("CREATE TABLE IF NOT EXISTS memories(pairing TEXT,id TEXT,text TEXT,dirty INTEGER DEFAULT 0,deleted INTEGER DEFAULT 0,PRIMARY KEY(pairing,id))");}
    private static void feedbackTable(SQLiteDatabase db){db.execSQL("CREATE TABLE IF NOT EXISTS feedback(id TEXT PRIMARY KEY,rating INTEGER,correction TEXT)");}
    @Override public void onUpgrade(SQLiteDatabase db,int old,int next){if(old<2)feedbackTable(db);if(old<3)db.execSQL("ALTER TABLE turns ADD COLUMN metadata TEXT DEFAULT '{}'");if(old<4){db.execSQL("ALTER TABLE turns ADD COLUMN pairing TEXT NOT NULL DEFAULT 'unpaired'");ContentValues v=new ContentValues();v.put("pairing",pairing());db.update("turns",v,null,null);}if(old<5)memoryTable(db);}
    void feedback(String id,int rating,String correction){ContentValues v=new ContentValues();v.put("id",id);v.put("rating",rating);v.put("correction",correction);getWritableDatabase().insertWithOnConflict("feedback",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    List<JSONObject> feedbackPending(String owner){List<JSONObject> rows=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT f.id AS response_id,f.rating,f.correction FROM feedback f JOIN turns t ON f.id=t.id WHERE t.pairing=? LIMIT 20",new String[]{owner})){while(c.moveToNext())rows.add(new JSONObject().put("response_id",c.getString(0)).put("rating",c.getInt(1)).put("correction",c.getString(2)));}catch(JSONException e){throw new IllegalStateException(e);}return rows;}
    void feedbackSynced(JSONObject f){getWritableDatabase().delete("feedback","id=? AND rating=? AND correction=?",new String[]{f.optString("response_id"),f.optString("rating"),f.optString("correction")});}
    void recover(){getWritableDatabase().execSQL("UPDATE turns SET mode='interrupted',reply='Sir, this request was interrupted. Please retry.',synced=0 WHERE mode='pending'");}
    void save(JSONObject t){ContentValues v=new ContentValues();for(String key:new String[]{"id","session","input","reply","mode","source","metadata"})v.put(key,t.optString(key));v.put("pairing",t.optString("pairing",pairing()));v.put("created",t.optLong("created"));v.put("synced",0);getWritableDatabase().insertWithOnConflict("turns",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    private JSONObject row(Cursor c) throws JSONException {JSONObject t=new JSONObject();for(int i=0;i<c.getColumnCount();i++)if(c.getColumnName(i).equals("created"))t.put("created",c.getLong(i));else t.put(c.getColumnName(i),c.getString(i));return t;}
    List<JSONObject> recent(String session,String owner,boolean scoped){List<JSONObject> rows=new ArrayList<>();String clause=scoped?"WHERE pairing=? ":"";ArrayList<String> args=new ArrayList<>();if(scoped)args.add(owner);if(session!=null){clause+=(scoped?"AND ":"WHERE ")+"session=? ";args.add(session);}try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM turns "+clause+"ORDER BY created DESC LIMIT 100",args.toArray(new String[0]))){while(c.moveToNext())rows.add(row(c));}catch(JSONException e){throw new IllegalStateException(e);}Collections.reverse(rows);return rows;}
    JSONArray pending(String owner){JSONArray rows=new JSONArray();int bytes=0;try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM turns WHERE synced=0 AND mode!='pending' AND pairing=? ORDER BY created LIMIT 20",new String[]{owner})){while(c.moveToNext()){JSONObject t=row(c);bytes+=t.toString().getBytes(StandardCharsets.UTF_8).length;if(bytes>200000)break;rows.put(t);}}catch(JSONException e){throw new IllegalStateException(e);}return rows;}
    void synced(JSONArray ids){for(int i=0;i<ids.length();i++){ContentValues v=new ContentValues();v.put("synced",1);getWritableDatabase().update("turns",v,"id=?",new String[]{ids.optString(i)});}}

    void importCloud(String owner,JSONArray rows){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            for(int i=0;i<rows.length();i++){JSONObject t=rows.optJSONObject(i);if(t==null||!t.optString("id").matches("[A-Za-z0-9_-]{1,128}"))continue;
                ContentValues v=new ContentValues();for(String key:new String[]{"id","session","input","reply","mode"})v.put(key,t.optString(key));
                v.put("source","firebase");v.put("metadata","{}");v.put("created",t.optLong("created"));v.put("pairing",owner);v.put("synced",1);
                // Keep local pending/interrupted edits; restored cloud turns are inserted once.
                db.insertWithOnConflict("turns",null,v,SQLiteDatabase.CONFLICT_IGNORE);
            }db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }

    JSONArray memoryPending(String owner){JSONArray rows=new JSONArray();int bytes=0;try(Cursor c=getReadableDatabase().rawQuery("SELECT id,text,deleted FROM memories WHERE pairing=? AND dirty=1 LIMIT 8",new String[]{owner})){while(c.moveToNext()){JSONObject row=new JSONObject().put("id",c.getString(0)).put("text",c.getString(1)).put("deleted",c.getInt(2)!=0);bytes+=row.toString().getBytes(StandardCharsets.UTF_8).length;if(bytes>18000)break;rows.put(row);}}catch(JSONException e){throw new IllegalStateException(e);}return rows;}
    void memoryMerged(String owner,JSONArray accepted,JSONArray rows){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            for(int i=0;i<accepted.length();i++){JSONObject a=accepted.optJSONObject(i);ContentValues v=new ContentValues();v.put("dirty",0);db.update("memories",v,"pairing=? AND id=? AND text=? AND deleted=?",new String[]{owner,a.optString("id"),a.optString("text"),a.optBoolean("deleted")?"1":"0"});}
            db.delete("memories","pairing=? AND dirty=0",new String[]{owner});
            for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);ContentValues v=new ContentValues();v.put("pairing",owner);v.put("id",r.optString("id"));v.put("text",r.optString("text"));v.put("dirty",0);v.put("deleted",0);db.insertWithOnConflict("memories",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    String memoryCommand(String owner,String input){
        String text=input.trim();java.util.regex.Matcher remember=java.util.regex.Pattern.compile("(?is)remember(?: that)?\\s+(.+)").matcher(text);
        if(remember.matches()){
            String value=remember.group(1).trim();if(value.isEmpty()||value.length()>2000)return "Sir, use a memory under 2,000 characters.";
            try(Cursor c=getReadableDatabase().rawQuery("SELECT count(*) FROM memories WHERE pairing=? AND deleted=0",new String[]{owner})){c.moveToFirst();if(c.getInt(0)>=50)return "Sir, memory holds fifty entries. Forget an old entry first.";}
            String id=UUID.randomUUID().toString().replace("-", "").substring(0,8);ContentValues v=new ContentValues();v.put("pairing",owner);v.put("id",id);v.put("text",value);v.put("dirty",1);v.put("deleted",0);getWritableDatabase().insertOrThrow("memories",null,v);return "Sir, saved on this phone as memory "+id+". It will sync when the service is reachable.";
        }
        if(text.matches("(?i)(?:show|list)(?: my| your)? memor(?:y|ies)|what do you remember(?: about me)?\\??")){
            StringBuilder answer=new StringBuilder();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,text FROM memories WHERE pairing=? AND deleted=0 ORDER BY rowid DESC LIMIT 10",new String[]{owner})){while(c.moveToNext())answer.append(c.getString(0)).append(". ").append(c.getString(1).substring(0,Math.min(800,c.getString(1).length()))).append("\n");}return "Sir, "+(answer.length()>0?answer.toString():"no memories are saved on this phone yet. Online memory sync runs after sign-in.");
        }
        java.util.regex.Matcher forget=java.util.regex.Pattern.compile("(?i)forget(?: memory)?\\s+([0-9a-f]{8})").matcher(text);
        if(forget.matches()){ContentValues v=new ContentValues();v.put("deleted",1);v.put("dirty",1);int n=getWritableDatabase().update("memories",v,"pairing=? AND id=? AND deleted=0",new String[]{owner,forget.group(1).toLowerCase(Locale.ROOT)});return n>0?"Sir, memory removed on this phone. Its deletion will sync online.":"Sir, that memory is not in this account's local memory.";}
        return null;
    }
}
