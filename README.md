<h1 align="center">VorwahlGuard</h1>

<p align="center">
  <strong>Block spam calls by number range — not one number at a time.</strong><br>
  An offline-only Android call screener with country and prefix rules.
</p>

<p align="center">
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android%2010%2B-3DDC84">
  <img alt="Language" src="https://img.shields.io/badge/core-Java%2017-orange">
  <img alt="UI" src="https://img.shields.io/badge/ui-Kotlin%20%2F%20Compose-7F52FF">
  <img alt="License" src="https://img.shields.io/badge/license-MIT-blue">
  <img alt="Network" src="https://img.shields.io/badge/internet%20permission-none-success">
</p>

---

## The problem

Spam call campaigns rotate their caller ID on every attempt. Blocking a single number
accomplishes nothing — the next call comes from a different one, and the displayed number is
usually spoofed anyway. Android's built-in blocklist only accepts exact numbers.

What stays roughly constant across a campaign is the **Vorwahl**: the country calling code, often
the operator range.

## The solution

VorwahlGuard registers as Android's call screening app and evaluates every incoming call against
your own prefix rules, on-device, before the phone rings.

Rules come from two places. Pick a country:

```
🇦🇹 Österreich   →   +43*      Lautlos
```

…or type a prefix directly:

```
+43663*          →   Sperren      one operator range
+436631234567    →   Zulassen     …except this one contact
PRIVATE          →   Lautlos      withheld caller ID
```

The most specific rule wins. On a tie, `Zulassen` beats `Lautlos` beats `Sperren`.

## Three actions

| | What happens |
|---|---|
| **Sperren** | The call is rejected immediately. The caller hears a busy or declined signal. |
| **Lautlos** | The phone does not ring. The call still appears in your call log and falls through to voicemail if the caller waits. |
| **Zulassen** | An allowlist entry. Beats any blocking rule that also matches. |

For a whole country code, **Lautlos** is the safer choice — a legitimate call from that country
still reaches your voicemail. The app suggests it when you create a `+XX*` rule.

## Your contacts are the exception

A prefix blocklist is blunt on purpose: `+43*  Sperren` stops a whole spam campaign, but it
would also stop your Austrian friends. Rather than carve out an allow rule for each of them by
hand, turn on **Kontakte immer zulassen** and *everyone already in your address book is waved
through* — no matter which `Sperren` or `Lautlos` rule they would otherwise match. The contact
exception has unconditional priority: it is checked before your rules are consulted at all.

- It runs entirely on-device against your local contacts. Like everything else here it needs no
  network, and nothing leaves the phone.
- It is **off by default** and requires the contacts read permission, which you grant explicitly.
- It fails closed: deny or later revoke that permission and the exception simply switches off —
  it never falls open into "allow everyone", it just stops treating anyone as a known contact.

## Features

- **Country picker and free-text prefixes** — both produce the same kind of rule
- **Withheld numbers** — a dedicated `PRIVATE` token for calls with no caller ID
- **Contacts whitelist** — optionally exempt everyone in your address book from every blocking
  rule, evaluated locally with unconditional priority
- **Honest about ambiguity** — `+1` is not "the USA", it is the USA, Canada and twenty
  Caribbean territories. The app tells you before you block them all.
- **Statistics** — what got screened, when, from where, by which rule
- **Genuinely offline** — the app does not hold the `INTERNET` permission and *cannot* make a
  network request. No accounts, no telemetry, no crash reporting, no ads.
- **Optional pseudonymisation** — store salted hashes instead of numbers; statistics still work

## Privacy

This is the whole point, so it is enforced structurally rather than promised in a policy:

| | |
|---|---|
| Network permission | **Not declared in the manifest.** Any network call would fail at runtime. CI rejects a PR that adds it. |
| Data leaving the device | None. There is no code path that could send it. |
| Third-party SDKs | None. |
| Country names and flags | Derived from `Locale` and computed Unicode codepoints. No downloads, no assets. |
| Number retention | Local SQLite, 90 days by default. Expired entries are purged automatically whenever the app or the screening service starts. Optionally numbers are stored as salted hashes instead — statistics still work; the toggle for this arrives with the settings screen. |

Verify it yourself: `app/src/main/AndroidManifest.xml` is short and has no
`android.permission.INTERNET`.

## Requirements

- Android 10 (API 29) or newer — required for `RoleManager.ROLE_CALL_SCREENING` and for
  silencing a call without rejecting it
- The call screening role, which you grant on first launch

> **Note:** Android allows exactly one call screening app at a time. Granting the role to
> VorwahlGuard disables the spam filter of your dialer app (typically Google Phone). You can
> switch back at any time in Android's default-apps settings.

## Installation

Download the latest APK from [Releases](https://github.com/mjanda0815/vorwahlguard/releases) and
install it. On first launch, grant the call screening role when prompted.

Not currently on Google Play — see [`PROJECT.md`](PROJECT.md) §9.

## Build from source

```bash
git clone https://github.com/mjanda0815/vorwahlguard.git
cd vorwahlguard
./gradlew build              # compile, unit tests, lint
./gradlew :app:assembleDebug # → app/build/outputs/apk/debug/
```

Requires **JDK 17** (the Android Gradle Plugin does not support newer JDKs for the build itself)
and the Android SDK. Everything else is resolved by Gradle.

## Architecture

A hexagonal split along the one boundary that matters here: the screening decision has nothing
to do with Android.

```
┌──────────────────────────────────────────────────────────┐
│  :app                    Kotlin, Android                 │
│                                                          │
│  VorwahlGuardScreeningService ┐  Compose UI (M3)         │
│  Room · Hilt · RoleManager    │  Übersicht/Regeln/…      │
└───────────────────────────────┼──────────────────────────┘
                                │ ports
┌───────────────────────────────▼──────────────────────────┐
│  :core-domain            Java 17, zero Android deps      │
│                                                          │
│  PhoneNumber · Pattern · Rule · RuleMatcher              │
│  CountryCatalog · conflict resolution                    │
│                                                          │
│  → runs on a plain JVM, tested with JUnit 5, no emulator │
└──────────────────────────────────────────────────────────┘
```

Why the split: the matching rules are where the bugs live, and an emulator is a terrible place
to hunt them. `./gradlew :core-domain:test` runs in under a second.

**Why not Kotlin everywhere?** The domain has no reason to know what a `Context` is. Why not
Java everywhere? Compose is a Kotlin compiler plugin. **Why not Spring?** Spring on Android has
been dead for years; Hilt provides compile-time dependency injection without reflection.

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for module boundaries and
[`PROJECT.md`](PROJECT.md) for the functional spec.

## The interesting constraint

`CallScreeningService.onScreenCall()` must respond within a few seconds or the system gives up
and lets the call through. That budget rules out a database query on the calling thread — rules
are served from an in-memory snapshot, and the call event is persisted *after* the response has
already been sent.

Everything else in the codebase follows from that, and from one rule: **if anything throws, the
call is allowed.** A screener that swallows a real call is worse than no screener at all.

## Contributing

Git Flow, feature branches, Conventional Commits. See [`CONTRIBUTING.md`](CONTRIBUTING.md).

## Roadmap

- [x] M0 — project skeleton, CI
- [x] M1 — domain core, fully unit-tested
- [x] M2 — screening service + role onboarding
- [x] M3 — Room persistence, retention purge
- [ ] M4 — Compose UI, country picker, de/en localisation
- [ ] M5 — hardening, `v1.0.0` release
- [ ] v1.1 — opt-in contact allowlisting, import/export of rules

## License

MIT — see [`LICENSE`](LICENSE).

## Disclaimer

Call screening behaviour depends on your Android version, OEM skin, and carrier. VorwahlGuard
cannot guarantee that a given call is blocked. Do not rely on it for anything where a missed
call has consequences.

Blocking a country code blocks legitimate calls from that country — that is not a bug, that is
the feature. And because spammers spoof their caller ID, `+43*` also blocks calls that never
touched Austria, while tomorrow's campaign may signal a German number instead. This app narrows
the funnel. It does not close it.
