# Think Smaart Reminder -> Your Google Sheet

Target Sheet is already fixed in `Code.gs`:
`1GJ9ezyQcY4GdmxOhw7VVUiN8uf3IfuhpwQhCB4PJQQg`

## One-time setup
1. Open your **Think Smaart Reminder** Google Sheet.
2. Go to **Extensions -> Apps Script**.
3. Delete the default sample code.
4. Paste the full `Code.gs` from this folder and Save.
5. In **Project Settings -> Script Properties**, add:
   - Property: `SYNC_KEY`
   - Value: a private key you choose (example: `TSR-2026-MyPrivateKey`)
6. Go to **Deploy -> New deployment -> Web app**.
   - Execute as: **Me**
   - Who has access: **Anyone**
7. Authorize Google Drive/Sheets access when Google asks.
8. Copy the Web App URL ending in `/exec`.
9. In the Android app open **Settings -> Google Sheets Sync**.
10. Paste the Web App URL and the same Sync Key, then tap **Save & Sync Now**.

## What syncs
- Users master list
- Profile photo saved to a Drive folder and its Drive URL written to `Users`
- Work, Payments, Delivery, Follow-up, Vendors
- Water tracking
- Health Schedule: Breakfast, Lunch, Lunch Break, Back To Work, Dinner
- Generic sync/event history in `User Data`
- A separate tab for each signed-in app user

Passwords are NEVER synced.
