package com.thinksmaart.reminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONObject;

import java.util.Map;

public final class AlarmScheduler {
    private AlarmScheduler() {}
    static final String PREFS = "think_smaart_native_alarms";
    static final String KEY_WATER = "water_alarm";
    static final int WATER_REQUEST_CODE = 918221;

    static String keyFor(long id) { return "reminder_" + id; }
    static int requestCodeFor(long id) {
        long v = id ^ (id >>> 32);
        int code = (int)(v & 0x7fffffff);
        return code == WATER_REQUEST_CODE ? code - 1 : code;
    }

    public static void scheduleReminder(Context context, JSONObject input) {
        try {
            long id = input.optLong("id", System.currentTimeMillis());
            long trigger = input.optLong("triggerAt", System.currentTimeMillis() + 60000L);
            if (trigger < System.currentTimeMillis() + 3000L) trigger = System.currentTimeMillis() + 3000L;
            input.put("id", id);
            input.put("triggerAt", trigger);
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(keyFor(id), input.toString()).apply();
            schedulePendingIntent(context, requestCodeFor(id), trigger, "reminder", id);
        } catch (Exception ignored) {}
    }

    public static void cancelReminder(Context context, long id) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = pendingIntent(context, requestCodeFor(id), "reminder", id);
        if (am != null) am.cancel(pi);
        pi.cancel();
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(keyFor(id)).apply();
        NotificationHelper.cancel(context, id);
    }

    public static void snoozeReminder(Context context, long id, int minutes) {
        try {
            SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String raw = sp.getString(keyFor(id), null);
            if (raw == null) return;
            JSONObject obj = new JSONObject(raw);
            long trigger = System.currentTimeMillis() + Math.max(1, minutes) * 60000L;
            obj.put("triggerAt", trigger);
            sp.edit().putString(keyFor(id), obj.toString()).apply();
            schedulePendingIntent(context, requestCodeFor(id), trigger, "reminder", id);
        } catch (Exception ignored) {}
    }

    public static void scheduleWater(Context context, int intervalMinutes) {
        try {
            int mins = Math.max(30, intervalMinutes);
            JSONObject obj = new JSONObject();
            obj.put("interval", mins);
            obj.put("triggerAt", System.currentTimeMillis() + mins * 60000L);
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_WATER, obj.toString()).apply();
            schedulePendingIntent(context, WATER_REQUEST_CODE, obj.getLong("triggerAt"), "water", -42L);
        } catch (Exception ignored) {}
    }

    public static void snoozeWater(Context context, int minutes) {
        try {
            SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String raw = sp.getString(KEY_WATER, null);
            JSONObject obj = raw == null ? new JSONObject() : new JSONObject(raw);
            if (!obj.has("interval")) obj.put("interval", 60);
            long trigger = System.currentTimeMillis() + Math.max(1, minutes) * 60000L;
            obj.put("triggerAt", trigger);
            sp.edit().putString(KEY_WATER, obj.toString()).apply();
            schedulePendingIntent(context, WATER_REQUEST_CODE, trigger, "water", -42L);
        } catch (Exception ignored) {}
    }

    public static void rescheduleAll(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        for (Map.Entry<String, ?> entry : sp.getAll().entrySet()) {
            try {
                if (!(entry.getValue() instanceof String)) continue;
                JSONObject obj = new JSONObject((String) entry.getValue());
                if (entry.getKey().equals(KEY_WATER)) {
                    int interval = obj.optInt("interval", 60);
                    long trigger = obj.optLong("triggerAt", 0);
                    if (trigger <= System.currentTimeMillis()) trigger = System.currentTimeMillis() + interval * 60000L;
                    schedulePendingIntent(context, WATER_REQUEST_CODE, trigger, "water", -42L);
                } else if (entry.getKey().startsWith("reminder_")) {
                    long id = obj.optLong("id", -1);
                    if (id < 0) continue;
                    long trigger = obj.optLong("triggerAt", 0);
                    boolean repeat = obj.optBoolean("repeatUntilCompleted", false);
                    if (trigger <= System.currentTimeMillis()) {
                        if (!repeat) continue;
                        trigger = System.currentTimeMillis() + 60000L;
                    }
                    schedulePendingIntent(context, requestCodeFor(id), trigger, "reminder", id);
                }
            } catch (Exception ignored) {}
        }
    }

    static void schedulePendingIntent(Context context, int requestCode, long triggerAt, String kind, long id) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = pendingIntent(context, requestCode, kind, id);
        long when = Math.max(System.currentTimeMillis() + 1000L, triggerAt);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            } else if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, when, pi);
            }
        } catch (SecurityException se) {
            if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            else am.set(AlarmManager.RTC_WAKEUP, when, pi);
        }
    }

    static PendingIntent pendingIntent(Context context, int requestCode, String kind, long id) {
        Intent i = new Intent(context, AlarmReceiver.class);
        i.setAction("com.thinksmaart.reminder.ALARM." + kind + "." + requestCode);
        i.putExtra("kind", kind);
        i.putExtra("id", id);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, requestCode, i, flags);
    }
}
