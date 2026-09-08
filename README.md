# Think Smaart Reminder V12

## New in V12
- Existing Work, Payment, Delivery, Follow-up, Vendor, Water and Health reminders remain.
- Due Date & Time remain separate.
- New Reminder / Alarm Date & Time for every business reminder.
- Popup + alarm uses Reminder Date & Time.
- Overdue status still uses Due Date & Time.
- Old reminders automatically use existing due date/time as their reminder time.
- Snooze changes only alarm time, not due date.
- Google Sheets sync includes Due Date, Due Time, Reminder Date and Reminder Time.
- Google Apps Script mapping fixed to use app `details` fields.


## V12.1 Web App connection
Google Apps Script Web App URL is preconfigured in the app:
https://script.google.com/macros/s/AKfycbwSKFmz2JlmxOrW3hpW-rlf2MvQdIJa6ZnmutHgCEg66aPi5NtdlibcFPdnOoPNY5aL/exec

The user only needs to enter the same private SYNC_KEY that was set in Apps Script Project Settings -> Script Properties, then tap Save & Sync Now. Do not share the key.

## V13 automatic employee sync
- GitHub Actions injects the repository secret `SYNC_KEY` only during APK build.
- The employee-facing app no longer asks for the Google Apps Script URL or Sync Key.
- Google Sheets sync is enabled automatically for all users.
