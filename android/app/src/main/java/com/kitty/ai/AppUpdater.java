package com.kitty.ai;
import org.json.JSONObject;
import okhttp3.*;
import java.net.URI;
import java.util.concurrent.TimeUnit;

/** Check metadata; Android's browser/store owns downloading and installing updates. */
final class AppUpdater {
    static final String MANIFEST="https://raw.githubusercontent.com/psychspy7/KITTY.AI/main/release/update.json";
    static final class UpdateInfo {
        final int code;final String version,url,notes;
        UpdateInfo(int c,String v,String u,String n){code=c;version=v;url=u;notes=n;}
    }
    static String releasePage(String download){
        URI uri=URI.create(download);
        if(!"https".equals(uri.getScheme())||!"github.com".equals(uri.getHost())||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||uri.getPort()!=-1||!uri.getPath().matches("/psychspy7/KITTY\\.AI/releases/download/v[0-9]+\\.[0-9]+\\.[0-9]+/KITTY-AI-release\\.apk"))throw new IllegalArgumentException("Invalid KITTY release link");
        return "https://github.com/psychspy7/KITTY.AI/releases/tag/"+uri.getPath().split("/")[5];
    }
    static UpdateInfo parse(JSONObject value){int code=value.optInt("latest_version_code");String name=value.optString("version_name");if(code<1||!name.matches("[0-9]+\\.[0-9]+\\.[0-9]+"))throw new IllegalArgumentException("Invalid release version");return new UpdateInfo(code,name,code>BuildConfig.VERSION_CODE?releasePage(value.optString("download_url")):"",value.optString("release_notes","See release notes."));}
    static UpdateInfo fetch() throws Exception {
        OkHttpClient c=new OkHttpClient.Builder().callTimeout(15,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build();
        try(Response r=c.newCall(new Request.Builder().url(MANIFEST).header("Cache-Control","no-cache").build()).execute()){
            if(!r.isSuccessful()||r.body()==null)throw new Exception("Update check unavailable. Please retry later.");okio.BufferedSource source=r.body().source();source.request(32001);if(source.getBuffer().size()>32000)throw new Exception("Update response too large");return parse(new JSONObject(source.readUtf8()));
        }
    }
}
