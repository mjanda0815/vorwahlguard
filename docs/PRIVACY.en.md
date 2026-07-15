# Privacy Policy – VorwahlGuard

**Last updated: 15 July 2026**

> Deutsche Fassung: [PRIVACY.md](PRIVACY.md)

## Summary

VorwahlGuard is a strictly offline app. It holds **no internet permission** and
is technically incapable of sending any data to a server, to the developer, or
to third parties. All processing happens exclusively on your device. There is no
analytics, no crash reporting, no advertising, and no tracking.

## 1. Data controller

Martin Janda
Contact: `<your-contact-email-here>`

## 2. Core principle: no data leaves the device

The app declares **neither** the `INTERNET` **nor** the `ACCESS_NETWORK_STATE`
permission in its Android manifest. Without those permissions an Android app
cannot open a network connection. This is not a promise but a technical barrier:
phone numbers and usage data **do not and cannot leave the device.**

## 3. What the app processes – and where it stays

All of the following is stored only locally, in the app's private,
app-only-accessible database:

| Data | Purpose | Location | Retention |
|---|---|---|---|
| Incoming call number (E.164) | Matching against your rules | local only | see below |
| Rule / block patterns you create | Call screening | local only | until you delete them |
| Log of screened calls (number/region, time, action) | Statistics & transparency | local only | 90 days default, configurable |

- **Outgoing calls are not processed and not logged.**
- Optionally, you can enable in the settings that logged numbers are stored
  **pseudonymised** (as a cryptographic hash with a device-local salt) instead
  of in clear text.
- The log is automatically purged after the configured retention period
  (90 days by default).

## 4. Permissions and their purpose

The app requests only the permissions its function requires:

- **Call Screening role** (`ROLE_CALL_SCREENING`): You actively select
  VorwahlGuard as your call-screening app in the system dialog. Only then can
  the app inspect incoming calls and – **strictly according to the rules you
  created** – block or silence them.
- **Read Contacts** (`READ_CONTACTS`, **optional, off by default**): Only if you
  enable the contacts exception does the app match incoming numbers against your
  address book to let known contacts through. The matching happens entirely on
  the device; no contact data is stored or transmitted. If the permission is
  revoked, the app behaves fail-closed (no matching).

## 5. No sharing with third parties

No data is shared with, sold to, or disclosed to third parties, because the app
is technically unable to transmit data. No analytics, advertising, or crash-
reporting SDKs are included.

## 6. Deletion

- You can delete rules at any time in the app.
- The log is automatically purged after the configured retention period.
- **Uninstalling the app removes all locally stored data completely.** No cloud
  backup takes place (`allowBackup` is disabled).

## 7. Your rights

Because no personal data ever leaves the device and the developer has no access
to your data at any point, the developer cannot and need not provide access,
erasure, or disclosure – you retain full control directly in the app and on your
device. You exercise your GDPR rights (access, rectification, erasure,
restriction) directly through the app and your device settings.

## 8. Changes to this policy

This policy is updated when the app's functionality changes. The date shown
above identifies the current version.
