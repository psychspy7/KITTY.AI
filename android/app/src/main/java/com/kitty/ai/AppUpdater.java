package com.kitty.ai;

import org.json.JSONObject;
import android.content.Context;
import android.content.pm.*;
import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.*;
import okhttp3.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** HTTPS release metadata + checksum and installed-certificate verified APKs. */
public final class AppUpdater {
    private AppUpdater() {}

    public static final class UpdateInfo {
        public final int latestVersionCode;
        public final String versionName;
        public final String downloadUrl;
        public final String sha256;
        public final String releaseNotes;

        UpdateInfo(int latestVersionCode, String versionName, String downloadUrl, String sha256, String releaseNotes) {
            this.latestVersionCode = latestVersionCode;
            this.versionName = versionName;
            this.downloadUrl = downloadUrl;
            this.sha256 = sha256;
            this.releaseNotes = releaseNotes;
        }

        public boolean isNewerThan(int installedVersionCode) {
            return latestVersionCode > installedVersionCode;
        }
    }

    static void validateDownload(UpdateInfo info){
        URI u=URI.create(info.downloadUrl);
        if(!"https".equals(u.getScheme())||!"github.com".equals(u.getHost())||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||!u.getPath().matches("/psychspy7/KITTY\\.AI/releases/download/v[0-9]+\\.[0-9]+\\.[0-9]+/KITTY-AI-release\\.apk"))throw new IllegalArgumentException("Update must be an immutable KITTY GitHub release");
        if(!info.sha256.matches("(?i)[a-f0-9]{64}"))throw new IllegalArgumentException("This release has no valid checksum yet");
    }

    public static File download(Context context,UpdateInfo info) throws Exception {
        validateDownload(info);
        File folder=new File(context.getCacheDir(),"updates");
        if(!folder.exists()&&!folder.mkdirs())throw new IllegalStateException("Cannot create update cache");
        File file=new File(folder,"KITTY-update.apk");
        OkHttpClient client=new OkHttpClient.Builder().followSslRedirects(false).callTimeout(180,java.util.concurrent.TimeUnit.SECONDS).build();
        try(Response response=client.newCall(new Request.Builder().url(info.downloadUrl).build()).execute()){
            if(!response.isSuccessful()||response.body()==null)throw new IllegalStateException("Download failed; try again later");
            if(response.body().contentLength()>200_000_000)throw new IllegalStateException("APK is too large");
            MessageDigest digest=MessageDigest.getInstance("SHA-256");long total=0;
            try(InputStream in=response.body().byteStream();FileOutputStream out=new FileOutputStream(file)){
                byte[] buffer=new byte[32768];int n;
                while((n=in.read(buffer))!=-1){total+=n;if(total>200_000_000)throw new IllegalStateException("APK is too large");out.write(buffer,0,n);digest.update(buffer,0,n);}
            }
            StringBuilder hex=new StringBuilder();for(byte value:digest.digest())hex.append(String.format(Locale.ROOT,"%02x",value&255));
            if(!hex.toString().equalsIgnoreCase(info.sha256))throw new IllegalStateException("Checksum mismatch. Installation blocked; retry the official release.");
            PackageManager pm=context.getPackageManager();
            PackageInfo candidate=pm.getPackageArchiveInfo(file.getAbsolutePath(),PackageManager.GET_SIGNING_CERTIFICATES),installed=pm.getPackageInfo(context.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
            if(candidate==null||!context.getPackageName().equals(candidate.packageName)||candidate.getLongVersionCode()!=info.latestVersionCode||candidate.getLongVersionCode()<=installed.getLongVersionCode())throw new IllegalStateException("APK identity or version does not match the update");
            if(candidate.signingInfo==null||installed.signingInfo==null||!signers(candidate).equals(signers(installed)))throw new IllegalStateException("Signing key differs from your installed app. Do not uninstall: keep your chats and ask Virat for a release signed with the original key.");
            return file;
        }catch(Exception e){if(file.exists())file.delete();throw e;}
    }
    private static Set<String> signers(PackageInfo info){Set<String> values=new HashSet<>();for(Signature s:info.signingInfo.getApkContentsSigners())values.add(s.toCharsString());return values;}

    public static UpdateInfo fetch(String manifestUrl) throws Exception {
        URI uri = URI.create(manifestUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException("Update manifest must use HTTPS");
        HttpURLConnection connection = (HttpURLConnection) new URL(manifestUrl).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "KITTY-AI-Android");
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("Update server returned HTTP " + code);
            String json = readLimited(connection.getInputStream(), 64 * 1024);
            JSONObject root = new JSONObject(json);
            int versionCode = root.getInt("latest_version_code");
            String versionName = root.optString("version_name", "");
            String downloadUrl = root.optString("download_url", "");
            if (!downloadUrl.isEmpty()) {
                URI apkUri = URI.create(downloadUrl);
                if (!"https".equalsIgnoreCase(apkUri.getScheme())) throw new IllegalArgumentException("APK URL must use HTTPS");
            }
            return new UpdateInfo(versionCode, versionName, downloadUrl,
                    root.optString("sha256", ""), root.optString("release_notes", ""));
        } finally {
            connection.disconnect();
        }
    }

    private static String readLimited(InputStream input, int limit) throws Exception {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                if (result.length() + count > limit) throw new IllegalArgumentException("Update manifest is too large");
                result.append(buffer, 0, count);
            }
        }
        return result.toString();
    }
}
