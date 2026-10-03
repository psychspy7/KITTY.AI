package com.kitty.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class Prefs {
    final SharedPreferences p;
    public Prefs(Context c) { p=c.getSharedPreferences("kitty",Context.MODE_PRIVATE); }
    public String url(){return p.getString("url",BuildConfig.KITTY_SERVER_URL.isEmpty()?"http://127.0.0.1:8765":BuildConfig.KITTY_SERVER_URL);}
    public boolean cloud(){return p.getBoolean("cloud",!BuildConfig.KITTY_SERVER_URL.isEmpty());}
    public String accountId(){return p.getString("account_id","");}
    public boolean admin(){return p.getString("role","").equals("admin");}
    public String email(){return p.getString("email","");}
    public void account(org.json.JSONObject value){
        String id=value.optString("id"),old=accountId();
        p.edit().putBoolean("cloud",true).putString("account_id",id).putString("role",value.optString("role","user")).putString("email",value.optString("email","")).putBoolean("backup",value.optBoolean("backup")).putBoolean("training",value.optBoolean("training")).apply();
        if(!id.equals(old)){String session=p.getString("account_session_"+id,UUID.randomUUID().toString());p.edit().putString("session",session).putString("account_session_"+id,session).apply();}
    }
    public void signOut() throws Exception {token("");p.edit().remove("account_id").remove("role").remove("email").putBoolean("backup",false).putBoolean("training",false).putString("session",UUID.randomUUID().toString()).apply();}
    public String country(){return p.getString("country","91");}
    public boolean directCalls(){return p.getBoolean("direct_calls",false);}
    public boolean speak(){return p.getBoolean("speak",true);}
    public String session(){synchronized(Prefs.class){String s=p.getString("session","");if(s.isEmpty()){s=UUID.randomUUID().toString();p.edit().putString("session",s).apply();}return s;}}
    public void newSession(){String s=UUID.randomUUID().toString();SharedPreferences.Editor e=p.edit().putString("session",s);if(!accountId().isEmpty())e.putString("account_session_"+accountId(),s);e.apply();}
    private SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(!ks.containsAlias("kitty-token")) {
            KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder("kitty-token",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            gen.generateKey();
        }
        return (SecretKey)ks.getKey("kitty-token",null);
    }
    public void token(String value) throws Exception {
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());
        p.edit().putString("token",Base64.encodeToString(c.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)),Base64.NO_WRAP)).putString("iv",Base64.encodeToString(c.getIV(),Base64.NO_WRAP)).apply();
    }
    public String token() {
        try {
            String value=p.getString("token","");if(value.isEmpty())return "";
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(p.getString("iv",""),Base64.NO_WRAP)));
            return new String(c.doFinal(Base64.decode(value,Base64.NO_WRAP)),java.nio.charset.StandardCharsets.UTF_8);
        } catch(Exception e){return "";}
    }
}
