package com.kitty.ai;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Fetches KITTY's signed-release manifest. APK installation remains a user-confirmed Android action. */
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
