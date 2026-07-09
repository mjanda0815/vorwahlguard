---
description: Build and install the app on the connected device
argument-hint: [debug|release]
---

Deploy the `${1:-debug}` build to the connected device.

Before anything else, confirm the device is reachable:

```bash
adb devices
```

If the list is empty, **stop**. Do not try to fix it by restarting the adb server — in WSL that
usually means `ADB_SERVER_SOCKET` is unset or the versions differ. Tell me to run
`source scripts/adb-env.sh` and point me at `docs/WSL-ADB.md`. You cannot run `usbipd` from here;
that needs an elevated PowerShell on the Windows host.

Then:

1. `./gradlew :app:assemble${1:-Debug}` — capitalise the variant for the task name.
2. **If the variant is `release`:** the signature differs from the debug build, so an existing
   debug install must go first:
   ```bash
   adb uninstall io.janda.vorwahlguard || true
   ```
   Warn me that this also revokes `ROLE_CALL_SCREENING` and I will have to grant it again.
3. `adb install -r <apk path>`
4. Report the exact APK path and its SHA-256.
5. For a release build, also run `apksigner verify --print-certs` and show me which signature
   schemes are present.

Never run `keytool`. Never read `~/.gradle/gradle.properties`, `*.jks`, or `keystore.properties`.
The signing credentials are deliberately outside your reach; if a build fails for want of them,
say so and stop rather than working around it.

Afterwards, offer to tail `adb logcat -s VorwahlGuard:* AndroidRuntime:E`. Never echo a phone
number out of logcat into the conversation.
