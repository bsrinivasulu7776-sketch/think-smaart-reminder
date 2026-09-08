package com.thinksmaart.reminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.Map;

public final class AlarmScheduler {
    private AlarmScheduler() {}
    static final String PREFS = "think_smaart_native_alarms";
    static final String KEY_WATER = "water_alarm";
    static final String KEY_HEALTH = "health_schedule";
    static final int WATER_REQUEST_CODE = 918221;
    static final int HEALTH_BREAKFAST_REQUEST_CODE = 928101;
    static final int HEALTH_LUNCH_REQUEST_CODE = 928102;
    static final int HEALTH_BACK_REQUEST_CODE = 928103;
    static final int HEALTH_DINNER_REQUEST_CODE = 928104;
    static final long HEALTH_BREAKFAST_ID = -1001L;
    static final long HEALTH_LUNCH_ID = -1002L;
    static final long HEALTH_BACK_ID = -1003L;
    static final long HEALTH_DINNER_ID = -1004L;

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

    public static void scheduleHealth(Context context, JSONObject input) {
        try {
            JSONObject cfg = new JSONObject(input == null ? "{}" : input.toString());
            if (!cfg.has("enabled")) cfg.put("enabled", true);
            if (!cfg.has("breakfast")) cfg.put("breakfast", "08:00");
            if (!cfg.has("lunch")) cfg.put("lunch", "13:00");
            if (!cfg.has("dinner")) cfg.put("dinner", "20:00");
            int breakMinutes = Math.max(15, Math.min(240, cfg.optInt("breakMinutes", 90)));
            cfg.put("breakMinutes", breakMinutes);
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_HEALTH, cfg.toString()).apply();
            cancelHealthPending(context);
            if (!cfg.optBoolean("enabled", true)) return;
            scheduleNextHealth(context, "health_breakfast", cfg.optString("breakfast", "08:00"));
            scheduleNextHealth(context, "health_lunch", cfg.optString("lunch", "13:00"));
            scheduleNextHealth(context, "health_dinner", cfg.optString("dinner", "20:00"));
            scheduleNextHealth(context, "health_back", addMinutes(cfg.optString("lunch", "13:00"), breakMinutes));
        } catch (Exception ignored) {}
    }

    public static void snoozeHealth(Context context, long id, int minutes) {
        String kind = healthKindForId(id);
        if (kind == null) return;
        long trigger = System.currentTimeMillis() + Math.max(1, minutes) * 60000L;
        schedulePendingIntent(context, healthRequestCode(kind), trigger, kind, healthId(kind));
    }

    static boolean isHealthId(long id) {
        return id == HEALTH_BREAKFAST_ID || id == HEALTH_LUNCH_ID || id == HEALTH_BACK_ID || id == HEALTH_DINNER_ID;
    }

    static void scheduleNextHealth(Context context, String kind, String hhmm) {
        long trigger = nextOccurrence(hhmm);
        schedulePendingIntent(context, healthRequestCode(kind), trigger, kind, healthId(kind));
    }

    static void scheduleNextHealthFromPrefs(Context context, String kind) {
        try {
            String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_HEALTH, null);
            if (raw == null) return;
            JSONObject cfg = new JSONObject(raw);
            if (!cfg.optBoolean("enabled", true)) return;
            String time;
            if ("health_breakfast".equals(kind)) time = cfg.optString("breakfast", "08:00");
            else if ("health_lunch".equals(kind)) time = cfg.optString("lunch", "13:00");
            else if ("health_dinner".equals(kind)) time = cfg.optString("dinner", "20:00");
            else if ("health_back".equals(kind)) time = addMinutes(cfg.optString("lunch", "13:00"), Math.max(15, Math.min(240, cfg.optInt("breakMinutes", 90))));
            else return;
            scheduleNextHealth(context, kind, time);
        } catch (Exception ignored) {}
    }

    static String addMinutes(String hhmm, int minutes) {
        try {
            String[] parts = hhmm.split(":");
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            int total = ((h * 60 + m + minutes) % (24 * 60) + (24 * 60)) % (24 * 60);
            return String.format(java.util.Locale.US, "%02d:%02d", total / 60, total % 60);
        } catch (Exception e) {
            return "14:30";
        }
    }

    static long nextOccurrence(String hhmm) {
        Calendar now = Calendar.getInstance();
        Calendar c = Calendar.getInstance();
        int h = 8, m = 0;
        try {
            String[] p = hhmm.split(":");
            h = Integer.parseInt(p[0]);
            m = Integer.parseInt(p[1]);
        } catch (Exception ignored) {}
        c.set(Calendar.HOUR_OF_DAY, Math.max(0, Math.min(23, h)));
        c.set(Calendar.MINUTE, Math.max(0, Math.min(59, m)));
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= now.getTimeInMillis() + 1000L) c.add(Calendar.DAY_OF_YEAR, 1);
        return c.getTimeInMillis();
    }

    static int healthRequestCode(String kind) {
        if ("health_breakfast".equals(kind)) return HEALTH_BREAKFAST_REQUEST_CODE;
        if ("health_lunch".equals(kind)) return HEALTH_LUNCH_REQUEST_CODE;
        if ("health_back".equals(kind)) return HEALTH_BACK_REQUEST_CODE;
        return HEALTH_DINNER_REQUEST_CODE;
    }

    static long healthId(String kind) {
        if ("health_breakfast".equals(kind)) return HEALTH_BREAKFAST_ID;
        if ("health_lunch".equals(kind)) return HEALTH_LUNCH_ID;
        if ("health_back".equals(kind)) return HEALTH_BACK_ID;
        return HEALTH_DINNER_ID;
    }

    static String healthKindForId(long id) {
        if (id == HEALTH_BREAKFAST_ID) return "health_breakfast";
        if (id == HEALTH_LUNCH_ID) return "health_lunch";
        if (id == HEALTH_BACK_ID) return "health_back";
        if (id == HEALTH_DINNER_ID) return "health_dinner";
        return null;
    }

    static void cancelHealthPending(Context context) {
        cancelPi(context, HEALTH_BREAKFAST_REQUEST_CODE, "health_breakfast", HEALTH_BREAKFAST_ID);
        cancelPi(context, HEALTH_LUNCH_REQUEST_CODE, "health_lunch", HEALTH_LUNCH_ID);
        cancelPi(context, HEALTH_BACK_REQUEST_CODE, "health_back", HEALTH_BACK_ID);
        cancelPi(context, HEALTH_DINNER_REQUEST_CODE, "health_dinner", HEALTH_DINNER_ID);
    }

    private static void cancelPi(Context context, int requestCode, String kind, long id) {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            PendingIntent pi = pendingIntent(context, requestCode, kind, id);
            if (am != null) am.cancel(pi);
            pi.cancel();
            NotificationHelper.cancel(context, id);
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
                } else if (entry.getKey().equals(KEY_HEALTH)) {
                    scheduleHealth(context, obj);
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
