package com.thinksmaart.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;

import org.json.JSONObject;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        PowerManager.WakeLock wl = null;
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ThinkSmaart:ReminderWakeLock");
                wl.acquire(10000L);
            }
            String kind = intent.getStringExtra("kind");
            long id = intent.getLongExtra("id", -1L);
            SharedPreferences sp = context.getSharedPreferences(AlarmScheduler.PREFS, Context.MODE_PRIVATE);

            if ("water".equals(kind)) {
                String raw = sp.getString(AlarmScheduler.KEY_WATER, null);
                if (raw == null) return;
                JSONObject obj = new JSONObject(raw);
                NotificationHelper.showAlarm(context, -42L, "Drink Water 💧",
                        "Think Smaart: Time to drink water.", "Water");
                int interval = Math.max(30, obj.optInt("interval", 60));
                long next = System.currentTimeMillis() + interval * 60000L;
                obj.put("triggerAt", next);
                sp.edit().putString(AlarmScheduler.KEY_WATER, obj.toString()).apply();
                AlarmScheduler.schedulePendingIntent(context, AlarmScheduler.WATER_REQUEST_CODE, next, "water", -42L);
                return;
            }

            if (kind != null && kind.startsWith("health_")) {
                String raw = sp.getString(AlarmScheduler.KEY_HEALTH, null);
                if (raw == null) return;
                JSONObject cfg = new JSONObject(raw);
                if (!cfg.optBoolean("enabled", true)) return;
                String title = "Health Reminder";
                String body = "Time for your healthy routine.";
                if ("health_breakfast".equals(kind)) {
                    title = "Breakfast Time 🍳";
                    body = "Good morning. Breakfast time — eat a balanced meal and start the day well.";
                } else if ("health_lunch".equals(kind)) {
                    title = "Lunch Time 🍱";
                    body = "Lunch time. Eat calmly, hydrate, and take your planned lunch break.";
                } else if ("health_back".equals(kind)) {
                    title = "Back to Work 💼";
                    body = "Lunch break complete. Time to get back to work.";
                } else if ("health_dinner".equals(kind)) {
                    title = "Dinner Time 🍽️";
                    body = "Dinner time. Keep it comfortable and avoid eating too close to bedtime when possible.";
                }
                NotificationHelper.showAlarm(context, id, title, body, "Health Schedule");
                AlarmScheduler.scheduleNextHealthFromPrefs(context, kind);
                return;
            }

            String raw = sp.getString(AlarmScheduler.keyFor(id), null);
            if (raw == null) return; // reminder was completed/deleted
            JSONObject obj = new JSONObject(raw);
            String title = obj.optString("title", "Reminder");
            String cat = obj.optString("category", "Work");
            String notes = obj.optString("notes", "");
            String body = notes == null || notes.isEmpty() ? (cat + " reminder is due now") : notes;
            NotificationHelper.showAlarm(context, id, title, body, cat);

            if (obj.optBoolean("repeatUntilCompleted", false)) {
                long next = System.currentTimeMillis() + 30L * 60000L;
                obj.put("triggerAt", next);
                sp.edit().putString(AlarmScheduler.keyFor(id), obj.toString()).apply();
                AlarmScheduler.schedulePendingIntent(context, AlarmScheduler.requestCodeFor(id), next, "reminder", id);
            } else {
                sp.edit().remove(AlarmScheduler.keyFor(id)).apply();
            }
        } catch (Exception ignored) {
        } finally {
            try { if (wl != null && wl.isHeld()) wl.release(); } catch (Exception ignored) {}
        }
    }
}
