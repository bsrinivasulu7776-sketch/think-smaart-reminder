package com.thinksmaart.reminder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;

public final class NotificationHelper {
    private NotificationHelper() {}
    public static final String PREFS = "think_smaart_native_settings";
    private static final String CUSTOM_AUTHORITY = "com.thinksmaart.reminder.audio";

    public static void ensureChannels(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean vib = sp.getBoolean("vibrate", true);
        createChannel(context, nm, "bell_" + (vib ? "v" : "n"), "Think Smaart Bell", resourceUri(context, R.raw.bell), vib);
        createChannel(context, nm, "chime_" + (vib ? "v" : "n"), "Soft Chime", resourceUri(context, R.raw.chime), vib);
        createChannel(context, nm, "alert_" + (vib ? "v" : "n"), "Strong Alert", resourceUri(context, R.raw.alert), vib);
        createChannel(context, nm, "silent_" + (vib ? "v" : "n"), "Silent Reminder", null, vib);
        long cv = sp.getLong("custom_version", 0L);
        if (cv > 0 && new java.io.File(context.getFilesDir(), "custom_reminder_sound").exists()) {
            createChannel(context, nm, "custom_" + cv + "_" + (vib ? "v" : "n"), "My Custom Reminder Sound",
                    Uri.parse("content://" + CUSTOM_AUTHORITY + "/custom"), vib);
        }
    }

    private static Uri resourceUri(Context c, int resId) {
        return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + c.getPackageName() + "/" + resId);
    }

    private static void createChannel(Context context, NotificationManager nm, String suffix, String name, Uri sound, boolean vibrate) {
        String id = "think_smaart_" + suffix;
        if (nm.getNotificationChannel(id) != null) return;
        NotificationChannel ch = new NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Think Smaart business and water reminders");
        ch.enableLights(true);
        ch.enableVibration(vibrate);
        if (vibrate) ch.setVibrationPattern(new long[]{0, 300, 120, 300});
        AudioAttributes aa = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        ch.setSound(sound, aa);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
    }

    static String currentChannel(Context context) {
        ensureChannels(context);
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String mode = sp.getString("sound_mode", "bell");
        boolean vib = sp.getBoolean("vibrate", true);
        String vn = vib ? "v" : "n";
        if ("custom".equals(mode)) {
            long cv = sp.getLong("custom_version", 0L);
            if (cv > 0 && new java.io.File(context.getFilesDir(), "custom_reminder_sound").exists())
                return "think_smaart_custom_" + cv + "_" + vn;
            mode = "bell";
        }
        if (!"bell".equals(mode) && !"chime".equals(mode) && !"alert".equals(mode) && !"silent".equals(mode)) mode = "bell";
        return "think_smaart_" + mode + "_" + vn;
    }

    public static void showAlarm(Context context, long id, String title, String body, String category) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        int nid = notificationId(id);

        Intent alertIntent = new Intent(context, AlarmAlertActivity.class);
        alertIntent.putExtra("id", id);
        alertIntent.putExtra("title", title);
        alertIntent.putExtra("body", body);
        alertIntent.putExtra("category", category);
        alertIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent full = PendingIntent.getActivity(context, nid + 100000, alertIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(context, nid + 200000, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = new Notification.Builder(context, currentChannel(context))
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher))
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_MAX)
                .setAutoCancel(true)
                .setContentIntent(content)
                .setFullScreenIntent(full, true)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true);
        try { nm.notify(nid, b.build()); } catch (SecurityException ignored) {}
    }

    public static void cancel(Context context, long id) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(notificationId(id));
    }

    static int notificationId(long id) {
        if (id == -42L) return 420042;
        long v = id ^ (id >>> 32);
        return (int)(1000 + (v & 0x3fffffff));
    }
}
