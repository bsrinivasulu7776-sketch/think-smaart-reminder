# Think Smaart Reminder V14

V14 focuses on safer account recovery, automatic UI corrections, and automatic reminder permission setup.

## What changed

- Automatic UI/JavaScript update check from the GitHub `main` branch on app launch.
  - Most future form, login, button, report, styling and sync-logic corrections can arrive without reinstalling a new APK.
  - Native Android changes (alarm engine, permissions, receivers, signing, etc.) still require a new APK.
- On first app open, Android permission flow starts automatically:
  - Notifications
  - Exact alarms / Alarms & reminders
  - Full-screen alarm permission on supported Android versions
- Login / Sign Up buttons rewired.
- Added **Forgot Password?**.
- New account signup is registered in the `Users` Google Sheet.
- Passwords are **not stored as readable text**. The Sheet stores a salted password verifier only.
- Forgot Password sends a 6-digit code to the registered email and allows a new password to be set.
- Cloud login can restore reminders, water data and health schedule from Google Sheets on a clean device.
- Built-in `SYNC_KEY` moved out of `index.html`; GitHub Actions injects it into native Android code at build time.

## One-time Apps Script update required for V14 account recovery

1. Open the Think Smaart Google Sheet.
2. Extensions -> Apps Script.
3. Replace the existing `Code.gs` with `google-sheets/Code.gs` from this project.
4. Project Settings -> Script Properties:
   - Keep the existing `SYNC_KEY` unchanged.
   - `AUTH_PEPPER` is optional. If omitted, V14 uses `SYNC_KEY` for password verification protection.
5. Save.
6. Deploy -> Manage deployments -> Edit -> New version -> Deploy.
7. Keep Web App access as **Anyone**, execute as **Me**.
8. Google may request new authorization because Forgot Password uses `MailApp` to send reset codes. Approve the requested Apps Script permissions.

Do not send `SYNC_KEY` or `AUTH_PEPPER` in chat or screenshots.

## Build

Repository secret required:

- `SYNC_KEY`

GitHub Actions automatically injects the secret and builds:

`app/build/outputs/apk/debug/app-debug.apk`

Artifact name:

`Think-Smaart-Reminder-V14-APK`

## Important Android note

Android does not permit an app to force every permission from the APK installer screen. V14 requests the required reminder permissions automatically immediately after the user opens the app.

## Important update/signing note

The current GitHub workflow builds a debug APK. GitHub-hosted runners can generate different debug signing keys between builds. If Android says a future APK cannot update the installed app because signatures differ, a stable release-signing setup will be needed. V14's remote UI update system reduces how often a new APK is required.
