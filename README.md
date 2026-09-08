# Think Smaart Reminder — Native Android

Package: `com.thinksmaart.reminder`

Native features included:
- Android AlarmManager reminders that continue when the app is closed.
- Exact-alarm permission flow with inexact fallback.
- Android 13+ notification permission request.
- Full-screen lock-screen reminder activity (subject to device/full-screen permission policy).
- Reminder re-scheduling after reboot/app update.
- 30-minute repeat for reminders set to **Until Completed**.
- Snooze from the native alarm screen.
- Water reminders scheduled natively.
- Built-in Bell / Chime / Strong Alert / Silent notification channels.
- User-selected custom audio copied into native app storage and used for Android notification alarms.
- Existing Think Smaart web UI embedded locally in a WebView, including signup, category forms, reports, settings and local data.

## Build
Open this folder in Android Studio, or push it to GitHub. The included GitHub Actions workflow builds an installable `app-debug.apk`.

On first run, allow Notifications, Alarms & reminders, and Full-screen notifications if Android opens those settings. Android controls these permissions; apps cannot silently grant them during install.
