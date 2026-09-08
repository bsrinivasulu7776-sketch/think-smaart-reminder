# Google Sheets / Apps Script setup - V14

Target Sheet: Think Smaart Reminder

## Required

In Apps Script -> Project Settings -> Script Properties:

- `SYNC_KEY` = same secret already configured in GitHub repository Actions secrets.

Optional:

- `AUTH_PEPPER` = a different long random secret for password-verifier protection. If not configured, the script safely falls back to `SYNC_KEY`.

## Deploy

After replacing `Code.gs`:

1. Save the script.
2. Deploy -> Manage deployments.
3. Edit the existing Web app deployment.
4. Select **New version**.
5. Execute as: **Me**.
6. Who has access: **Anyone**.
7. Deploy and authorize the requested scopes.

Forgot Password uses `MailApp` to email a 6-digit code. The code expires after 10 minutes.

## Users sheet

V14 automatically expands the `Users` sheet with protected account fields:

- User ID
- Name
- Email
- Mobile
- Profile Photo URL
- Signup Date
- Last Sync
- Status
- Password Hash
- Password Salt
- Password Updated At
- Role
- Password Recovery

The actual password is never written to Google Sheets.
