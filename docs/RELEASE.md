# Release — signing and shipping a APK

The deliverable of M5 is a **signed release APK**, attached to a GitHub Release. Not an AAB —
that format only matters for Google Play, and this app ships outside it (see `PROJECT.md` §9).

---

## 1. The keystore

Generate it once. Keep it out of the repository — not merely gitignored, **outside the working
directory entirely**, so that no agent, no `git add -A` and no accidental `cp -r` can ever reach
it.

```bash
mkdir -p ~/keys && chmod 700 ~/keys

keytool -genkeypair -v \
  -keystore ~/keys/vorwahlguard-release.jks \
  -storetype PKCS12 \
  -alias vorwahlguard \
  -keyalg RSA -keysize 4096 \
  -validity 10000
```

`-storetype PKCS12` avoids the proprietary-format warning. 10000 days is roughly 27 years; a
certificate that expires makes every future update unsignable.

> **Lose this file and the app is dead.** Not "annoying to recover" — dead. Android identifies an
> app by package name *and* signing certificate. A differently-signed APK cannot update an
> installed one, on Play or by sideload. Back it up somewhere that is not this machine.

## 2. Credentials, and keeping them away from Claude

Put the secrets in **`~/.gradle/gradle.properties`**, never in the repo:

```properties
VG_STORE_FILE=/home/martin/keys/vorwahlguard-release.jks
VG_STORE_PASSWORD=…
VG_KEY_ALIAS=vorwahlguard
VG_KEY_PASSWORD=…
```

Gradle picks these up globally. Claude Code, running inside the repo, never has them in context
— and `.claude/settings.json` denies `Read` on `*.jks`, `keystore.properties` and
`local.properties` as a second layer. Do not paste a keystore password into a prompt. Ever.

## 2a. Setting up a second development machine

A PKCS12 keystore is a plain file with no machine binding — copying it over is enough, there is
nothing to "re-generate" or re-associate. **Do not run `keytool` again on the second machine** —
Android ties an installed app to the certificate it was first signed with, so a release signed
with a *different* keystore can never update an install signed by this one.

Two things need to travel from this machine to the new one. Do this once, then every `git clone`
/ `git pull` on the new machine just works.

### Step 1 — clone/pull the repo as usual

```bash
git clone git@github.com:mjanda0815/vorwahlguard.git   # or: git pull, if already cloned
```

Nothing keystore-related is in the repo (`.gitignore` excludes `*.jks`/`*.keystore`/`*.p12`) —
this step never touches signing material.

### Step 2 — transfer the keystore file

`~/keys/vorwahlguard-release.jks` (this machine) → `~/keys/vorwahlguard-release.jks` (the new
one). Pick one:

**Encrypted archive, any transport (recommended — works over any channel you already use,
e.g. syncing the `.gpg` file through a cloud drive is fine since it's encrypted):**

```bash
# here (this notebook)
mkdir -p ~/keys && chmod 700 ~/keys   # if not already done
gpg -c ~/keys/vorwahlguard-release.jks
# produces ~/keys/vorwahlguard-release.jks.gpg — enter a passphrase when prompted,
# and give that passphrase to your other machine out of band (not the same channel
# you send the file through)
```

```bash
# on the new machine, after the .gpg file has arrived
mkdir -p ~/keys && chmod 700 ~/keys
gpg -d vorwahlguard-release.jks.gpg > ~/keys/vorwahlguard-release.jks
chmod 600 ~/keys/vorwahlguard-release.jks
rm vorwahlguard-release.jks.gpg   # the encrypted copy; delete once decrypted
```

**Or, if both machines are on the same local network:**

```bash
scp ~/keys/vorwahlguard-release.jks other-machine:~/keys/
```

**Or:** copy it via a USB drive — same file, `chmod 700 ~/keys` / `chmod 600` the file on the
new machine afterward either way.

Not git, not an unencrypted cloud sync, not email/chat.

### Step 3 — set the credentials on the new machine

Create (or edit) `~/.gradle/gradle.properties` on the new machine — **not** in the repo:

```properties
VG_STORE_FILE=/path/on/the/new/machine/keys/vorwahlguard-release.jks
VG_STORE_PASSWORD=<same password as this machine>
VG_KEY_ALIAS=vorwahlguard
VG_KEY_PASSWORD=<same password as this machine>
```

`VG_STORE_FILE` is the only value that may differ between machines (it's a local path);
`VG_STORE_PASSWORD`/`VG_KEY_ALIAS`/`VG_KEY_PASSWORD` must be byte-identical to what you set here.
Get them from a password manager, not by re-typing from memory — a typo here fails silently as
"wrong password" at sign time, not at property-read time.

### Step 4 — verify

```bash
./gradlew :app:assembleRelease
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

The printed certificate fingerprint must match what this machine produces for the same command
— that confirms the transfer worked and both machines sign identically.

### Keeping a backup

Keep at least one copy of the keystore file that is bound to neither machine (a password
manager attachment, an encrypted archive in cloud storage) — see the warning in §1: losing the
last copy is unrecoverable, not merely inconvenient.

## 3. `app/build.gradle.kts`

The signing config must be **optional**, or CI (which has no keystore) cannot build:

```kotlin
android {
    signingConfigs {
        create("release") {
            val storePath = providers.gradleProperty("VG_STORE_FILE").orNull
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = providers.gradleProperty("VG_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("VG_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("VG_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
                .takeIf { providers.gradleProperty("VG_STORE_FILE").isPresent }
        }
    }
}
```

## 4. R8, and the bug you will only see in release

`isMinifyEnabled = true` is where a debug-clean app breaks. Hilt and Room ship consumer ProGuard
rules; libphonenumber does not have the same guarantees — it loads its metadata at runtime by
resource name, which R8 cannot see.

Starting point in `app/proguard-rules.pro`:

```proguard
-keep class com.google.i18n.phonenumbers.** { *; }
-keepclassmembers class com.google.i18n.phonenumbers.** { *; }
```

**Verified on-device on 2026-07-15** (release build v0.2.0, versionCode 148, v2 signature): the
country picker opened and a `+43*` rule was created with no crash and no normalisation error — the
keep-rules above are sufficient for the picker and number-normalisation paths. Re-run this check
after any libphonenumber version bump or change to these rules; it is the only way to know. The
failure mode is an empty country picker or a normalisation exception, visible only in a release
build on a real device. Do not ship a release APK you have only tested as debug.

The `CallScreeningService` itself survives because it is named in the manifest.

## 5. Build and verify

```bash
./gradlew :app:assembleRelease
```

Then check what actually came out — do not trust that it is signed because the build succeeded:

```bash
apksigner verify --print-certs --verbose app/build/outputs/apk/release/app-release.apk
```

You want the certificate you generated, and v2/v3 signature schemes confirmed. With `minSdk 29`
the v1 (JAR) scheme is dead weight; check whether AGP still emits it and turn it off if so.

`zipalign` is handled by AGP. If `apksigner` complains about alignment, something is wrong with
the build, not with your invocation.

## 6. Install on the device

```bash
adb uninstall io.janda.vorwahlguard   # required if a debug build is present
adb install -r app/build/outputs/apk/release/app-release.apk
```

Two things bite here, in order:

1. **Debug and release have different signatures.** Installing release over debug fails with
   `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Uninstall first.
2. **Uninstalling revokes `ROLE_CALL_SCREENING`.** After the release install, the app holds no
   role and screens nothing. Walk through onboarding again. If the dashboard does not warn you
   about this loudly, that is a bug in the dashboard.

Then the manual test that no CI job can replace:

```
Regel: Österreich → +43* → Lautlos
Anruf von einer österreichischen Nummer:
  Telefon klingelt nicht, Anruf steht im Anrufprotokoll.

Regel auf Sperren umstellen, erneut anrufen:
  Anrufer bekommt sofort ein Ablehnsignal.
```

## 7. GitHub Release

```bash
git checkout main && git pull
gh release create v1.0.0 \
  app/build/outputs/apk/release/app-release.apk \
  --title "VorwahlGuard 1.0.0" \
  --notes-file CHANGELOG.md
```

Include the SHA-256 of the APK in the release notes so anyone can verify what they downloaded:

```bash
sha256sum app/build/outputs/apk/release/app-release.apk
```

## 8. Signing in CI — deliberately not done

The keystore could live in GitHub Secrets as base64 and be decoded in the workflow. For a
single-maintainer project it adds an exfiltration path (any workflow on any branch could print
it) in exchange for saving one local command. **v1.0 signs locally.** Revisit if a second
maintainer appears.
