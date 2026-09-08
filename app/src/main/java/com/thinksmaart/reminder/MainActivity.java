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

import java.io.File;
import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class MainActivity extends Activity {
    private static final int REQ_NOTIFICATIONS = 7001;
    private static final int REQ_FILE = 7002;
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        webView.loadUrl("file:///android_asset/web/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE && filePathCallback != null) {
            Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    private void requestAndroidPermissions() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }

        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                try {
                    Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Exception ignored) {}
            }
        }

        if (Build.VERSION.SDK_INT >= 34) {
            try {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null && !nm.canUseFullScreenIntent()) {
                    Intent i = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                }
            } catch (Exception ignored) {}
        }
    }

    public class NativeBridge {
        private final Context appContext;
        NativeBridge(Context context) { this.appContext = context.getApplicationContext(); }

        @JavascriptInterface
        public boolean isNative() { return true; }

        @JavascriptInterface
        public void requestPermissions() {
            runOnUiThread(() -> requestAndroidPermissions());
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
                    .putString("url", url == null ? "" : url.trim())
                    .putString("key", key == null ? "" : key)
                    .apply();
        }

        @JavascriptInterface
        public String getGoogleSheetsUrl() {
            return appContext.getSharedPreferences("think_smaart_sync", MODE_PRIVATE)
                    .getString("url", "");
        }

        @JavascriptInterface
        public void syncToGoogleSheets(String payload) {
            final String endpoint = appContext.getSharedPreferences("think_smaart_sync", MODE_PRIVATE)
                    .getString("url", "");
            if (endpoint == null || endpoint.trim().isEmpty()) {
                notifySyncResult(false, "Google Sheets URL not configured");
                return;
            }

            new Thread(() -> {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(endpoint.trim());
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
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(body);
                    }

                    int code = conn.getResponseCode();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(
                            (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream(),
                            StandardCharsets.UTF_8));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                    reader.close();

                    boolean ok = code >= 200 && code < 300;
                    String msg = ok ? "Synced to Google Sheets" : ("HTTP " + code);
                    try {
                        JSONObject resp = new JSONObject(response.toString());
                        if (resp.has("ok")) ok = resp.optBoolean("ok", ok);
                        if (resp.has("message")) msg = resp.optString("message", msg);
                        else if (resp.has("error")) msg = resp.optString("error", msg);
                    } catch (Exception ignored) {}
                    notifySyncResult(ok, msg);
                } catch (Exception e) {
                    notifySyncResult(false, "Sync error: " + e.getClass().getSimpleName());
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }).start();
        }

        private void notifySyncResult(boolean ok, String message) {
            runOnUiThread(() -> {
                if (webView == null) return;
                try {
                    JSONObject o = new JSONObject();
                    o.put("ok", ok);
                    o.put("message", message == null ? "" : message);
                    String js = "window.onNativeSyncResult && window.onNativeSyncResult(" +
                            (ok ? "true" : "false") + "," + JSONObject.quote(o.optString("message")) + ");";
                    webView.evaluateJavascript(js, null);
                } catch (Exception ignored) {}
            });
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
