package com.thinksmaart.reminder;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

public class MainActivity extends Activity {
    private static final int REQ_NOTIFICATIONS = 7001;
    private static final int REQ_FILE = 7002;
    private static final int REQ_EXACT_ALARM = 7003;
    private static final int REQ_FULL_SCREEN = 7004;

    private static final String BUILT_IN_SYNC_URL = "https://script.google.com/macros/s/AKfycbwSKFmz2JlmxOrW3hpW-rlf2MvQdIJa6ZnmutHgCEg66aPi5NtdlibcFPdnOoPNY5aL/exec";
    private static final String BUILT_IN_SYNC_KEY = "__THINK_SMAART_SYNC_KEY__";
    private static final String REMOTE_UI_URL = "https://raw.githubusercontent.com/bsrinivasulu7776-sketch/think-smaart-reminder/main/app/src/main/assets/web/index.html";
    private static final String UI_CACHE_FILE = "think_smaart_remote_ui_v14.html";

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private long appStartedAt;
    private String currentUiHash = "";
    private boolean notificationAttempted = false;
    private boolean exactAlarmAttempted = false;
    private boolean fullScreenAttempted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appStartedAt = System.currentTimeMillis();
        NotificationHelper.ensureChannels(this);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setBuiltInZoomControls(false);
        ws.setDisplayZoomControls(false);

        webView.addJavascriptInterface(new NativeBridge(this), "NativeApp");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }
                MainActivity.this.filePathCallback = filePathCallback;
                try {
                    Intent intent = fileChooserParams.createIntent();
                    startActivityForResult(intent, REQ_FILE);
                    return true;
                } catch (Exception e) {
                    MainActivity.this.filePathCallback = null;
                    Toast.makeText(MainActivity.this, "File picker open avvaledu", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });

        loadBestAvailableUi();
        refreshRemoteUiInBackground();

        // Android does not show all permissions on the APK installer screen. V14 starts
        // the permission flow automatically as soon as the app is opened.
        webView.postDelayed(this::requestNextReminderPermission, 1200);
    }

    private void loadBestAvailableUi() {
        String html = readFileText(new File(getFilesDir(), UI_CACHE_FILE));
        if (!isValidUi(html)) html = readAssetText("web/index.html");
        if (isValidUi(html)) loadHtml(html);
        else webView.loadUrl("file:///android_asset/web/index.html");
    }

    private void loadHtml(String html) {
        currentUiHash = sha256(html);
        webView.loadDataWithBaseURL("file:///android_asset/web/", html, "text/html", "UTF-8", null);
    }

    private void refreshRemoteUiInBackground() {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(REMOTE_UI_URL + "?ts=" + System.currentTimeMillis());
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(4500);
                conn.setReadTimeout(5000);
                conn.setUseCaches(false);
                conn.setRequestProperty("Accept", "text/html,text/plain,*/*");
                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) return;
                String html = readStreamText(conn.getInputStream());
                if (!isValidUi(html)) return;
                String hash = sha256(html);
                if (hash.equals(currentUiHash)) return;

                File cache = new File(getFilesDir(), UI_CACHE_FILE);
                try (FileOutputStream out = new FileOutputStream(cache, false)) {
                    out.write(html.getBytes(StandardCharsets.UTF_8));
                }

                long elapsed = System.currentTimeMillis() - appStartedAt;
                if (elapsed < 2800) {
                    runOnUiThread(() -> loadHtml(html));
                } else {
                    runOnUiThread(() -> {
                        if (webView != null) {
                            webView.evaluateJavascript("window.toast && window.toast('Latest app corrections downloaded • next open lo automatic ga apply avuthayi');", null);
                        }
                    });
                }
            } catch (Exception ignored) {
                // Offline or GitHub unavailable: bundled/cached UI keeps working.
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private boolean isValidUi(String html) {
        return html != null && html.length() > 20000 && html.contains("Think Smaart Reminder")
                && html.contains("id=\"appShell\"") && html.contains("<script>");
    }

    private String readAssetText(String path) {
        try (InputStream in = getAssets().open(path)) {
            return readStreamText(in);
        } catch (Exception e) {
            return "";
        }
    }

    private String readFileText(File file) {
        if (file == null || !file.exists()) return "";
        try (InputStream in = new FileInputStream(file)) {
            return readStreamText(in);
        } catch (Exception e) {
            return "";
        }
    }

    private String readStreamText(InputStream in) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        reader.close();
        return sb.toString();
    }

    private String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(text == null ? 0 : text.hashCode());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE && filePathCallback != null) {
            Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
            return;
        }
        if (requestCode == REQ_EXACT_ALARM || requestCode == REQ_FULL_SCREEN) {
            webView.postDelayed(this::requestNextReminderPermission, 450);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFICATIONS && webView != null) {
            webView.postDelayed(this::requestNextReminderPermission, 450);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    private void requestNextReminderPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && !notificationAttempted) {
            notificationAttempted = true;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
            return;
        }

        if (Build.VERSION.SDK_INT >= 31 && !exactAlarmAttempted) {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                exactAlarmAttempted = true;
                try {
                    Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + getPackageName()));
                    startActivityForResult(i, REQ_EXACT_ALARM);
                    return;
                } catch (Exception ignored) {}
            }
        }

        if (Build.VERSION.SDK_INT >= 34 && !fullScreenAttempted) {
            try {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null && !nm.canUseFullScreenIntent()) {
                    fullScreenAttempted = true;
                    Intent i = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:" + getPackageName()));
                    startActivityForResult(i, REQ_FULL_SCREEN);
                }
            } catch (Exception ignored) {}
        }
    }

    private interface HttpCallback {
        void done(boolean httpOk, String response);
    }

    private void postJsonAsync(String endpoint, String payload, HttpCallback callback) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            boolean httpOk = false;
            String response = "";
            try {
                URL url = new URL(endpoint == null || endpoint.trim().isEmpty() ? BUILT_IN_SYNC_URL : endpoint.trim());
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(20000);
                conn.setInstanceFollowRedirects(true);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "text/plain; charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json,text/plain,*/*");

                byte[] body = (payload == null ? "{}" : payload).getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(body.length);
                try (OutputStream os = conn.getOutputStream()) { os.write(body); }

                int code = conn.getResponseCode();
                httpOk = code >= 200 && code < 300;
                InputStream stream = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
                if (stream != null) response = readStreamText(stream).trim();
                if (response.isEmpty()) response = "{\"ok\":" + (httpOk ? "true" : "false") + ",\"error\":\"HTTP " + code + "\"}";
            } catch (Exception e) {
                response = "{\"ok\":false,\"error\":" + JSONObject.quote("Connection error: " + e.getClass().getSimpleName()) + "}";
            } finally {
                if (conn != null) conn.disconnect();
            }
            final boolean resultOk = httpOk;
            final String resultResponse = response;
            runOnUiThread(() -> callback.done(resultOk, resultResponse));
        }).start();
    }

    public class NativeBridge {
        private final Context appContext;
        NativeBridge(Context context) { this.appContext = context.getApplicationContext(); }

        @JavascriptInterface
        public boolean isNative() { return true; }

        @JavascriptInterface
        public String getBuiltInSyncUrl() { return BUILT_IN_SYNC_URL; }

        @JavascriptInterface
        public String getBuiltInSyncKey() {
            return BUILT_IN_SYNC_KEY.startsWith("__THINK_") ? "" : BUILT_IN_SYNC_KEY;
        }

        @JavascriptInterface
        public String getAppVersion() { return "14.0"; }

        @JavascriptInterface
        public void requestPermissions() {
            runOnUiThread(() -> {
                notificationAttempted = false;
                exactAlarmAttempted = false;
                fullScreenAttempted = false;
                requestNextReminderPermission();
            });
        }

        @JavascriptInterface
        public void scheduleReminder(String json) {
            try {
                JSONObject obj = new JSONObject(json);
                AlarmScheduler.scheduleReminder(appContext, obj);
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Reminder schedule error", Toast.LENGTH_SHORT).show());
            }
        }

        @JavascriptInterface
        public void cancelReminder(String id) {
            try { AlarmScheduler.cancelReminder(appContext, Long.parseLong(id)); } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void scheduleWater(int intervalMinutes) {
            AlarmScheduler.scheduleWater(appContext, Math.max(30, intervalMinutes));
        }

        @JavascriptInterface
        public void scheduleHealthSchedule(String json) {
            try {
                JSONObject obj = new JSONObject(json == null ? "{}" : json);
                AlarmScheduler.scheduleHealth(appContext, obj);
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Health schedule error", Toast.LENGTH_SHORT).show());
            }
        }

        @JavascriptInterface
        public void setSoundMode(String mode, boolean vibrate) {
            appContext.getSharedPreferences(NotificationHelper.PREFS, MODE_PRIVATE)
                    .edit().putString("sound_mode", mode == null ? "bell" : mode)
                    .putBoolean("vibrate", vibrate).apply();
            NotificationHelper.ensureChannels(appContext);
        }

        @JavascriptInterface
        public void saveCustomSound(String dataUrl, String mime, String name) {
            try {
                int comma = dataUrl.indexOf(',');
                String b64 = comma >= 0 ? dataUrl.substring(comma + 1) : dataUrl;
                byte[] bytes;
                if (Build.VERSION.SDK_INT >= 26) bytes = Base64.getDecoder().decode(b64);
                else bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
                File f = new File(appContext.getFilesDir(), "custom_reminder_sound");
                try (FileOutputStream out = new FileOutputStream(f, false)) { out.write(bytes); }
                String safeMime = (mime == null || mime.isEmpty()) ? "audio/*" : mime;
                long version = System.currentTimeMillis();
                appContext.getSharedPreferences(NotificationHelper.PREFS, MODE_PRIVATE).edit()
                        .putString("custom_mime", safeMime)
                        .putString("custom_name", name == null ? "My Sound" : name)
                        .putLong("custom_version", version)
                        .putString("sound_mode", "custom")
                        .apply();
                NotificationHelper.ensureChannels(appContext);
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Custom sound native save avvaledu", Toast.LENGTH_SHORT).show());
            }
        }

        @JavascriptInterface
        public void setGoogleSheetsConfig(String url, String key) {
            appContext.getSharedPreferences("think_smaart_sync", MODE_PRIVATE)
                    .edit()
                    .putString("url", url == null || url.trim().isEmpty() ? BUILT_IN_SYNC_URL : url.trim())
                    .putString("key", key == null || key.isEmpty() ? getBuiltInSyncKey() : key)
                    .apply();
        }

        @JavascriptInterface
        public String getGoogleSheetsUrl() {
            return appContext.getSharedPreferences("think_smaart_sync", MODE_PRIVATE)
                    .getString("url", BUILT_IN_SYNC_URL);
        }

        @JavascriptInterface
        public void backendRequest(String requestId, String payload) {
            final String id = requestId == null ? "" : requestId;
            postJsonAsync(BUILT_IN_SYNC_URL, payload, (httpOk, response) -> {
                if (webView == null) return;
                String js = "window.onNativeBackendResult && window.onNativeBackendResult(" +
                        JSONObject.quote(id) + "," + (httpOk ? "true" : "false") + "," + JSONObject.quote(response) + ");";
                webView.evaluateJavascript(js, null);
            });
        }

        @JavascriptInterface
        public void syncToGoogleSheets(String payload) {
            String endpoint = appContext.getSharedPreferences("think_smaart_sync", MODE_PRIVATE)
                    .getString("url", BUILT_IN_SYNC_URL);
            postJsonAsync(endpoint, payload, (httpOk, response) -> {
                boolean ok = httpOk;
                String msg = ok ? "Synced to Google Sheets" : "Sync failed";
                try {
                    JSONObject resp = new JSONObject(response == null ? "{}" : response);
                    if (resp.has("ok")) ok = resp.optBoolean("ok", ok);
                    if (resp.has("message")) msg = resp.optString("message", msg);
                    else if (resp.has("error")) msg = resp.optString("error", msg);
                } catch (Exception ignored) {}
                notifySyncResult(ok, msg);
            });
        }

        private void notifySyncResult(boolean ok, String message) {
            if (webView == null) return;
            String js = "window.onNativeSyncResult && window.onNativeSyncResult(" +
                    (ok ? "true" : "false") + "," + JSONObject.quote(message == null ? "" : message) + ");";
            webView.evaluateJavascript(js, null);
        }

        @JavascriptInterface
        public void removeCustomSound() {
            try { new File(appContext.getFilesDir(), "custom_reminder_sound").delete(); } catch (Exception ignored) {}
            appContext.getSharedPreferences(NotificationHelper.PREFS, MODE_PRIVATE).edit()
                    .remove("custom_mime").remove("custom_name").remove("custom_version")
                    .putString("sound_mode", "bell").apply();
            NotificationHelper.ensureChannels(appContext);
        }
    }
}
