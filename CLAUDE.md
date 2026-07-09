# CLAUDE.md

Operating manual for AI agents working in this repository. Read `PROJECT.md` for the
functional specification and `docs/ARCHITECTURE.md` for module boundaries before writing code.

---

## 1. What this project is

VorwahlGuard is an **offline-only Android call screening app**. It blocks or silences incoming
calls whose E.164 number matches a user-defined prefix — either picked from a country list
(`Österreich` → `+43*`) or typed directly (`+43663*`, `+436631234567`). It keeps a local
statistic of screened calls.

**Non-negotiable product constraints:**

- No `android.permission.INTERNET`. Ever. The app must not be able to make a network call.
- No analytics, no crash reporting SDK, no telemetry, no ads.
- Phone numbers never leave the device and are never written to a log with `Log.d(...)`.

If a change would require the `INTERNET` permission, **stop and ask the user first**.

---

## 2. Tech stack (fixed — do not substitute)

| Layer | Choice | Reason |
|---|---|---|
| Build | Gradle Kotlin DSL + Version Catalog (`gradle/libs.versions.toml`) | Reproducible dependency management |
| JDK for Gradle/AGP | **JDK 17** | AGP requires it. Not 21, not 25. |
| `:core-domain` | **Java 17**, `java-library` plugin, zero Android deps | Pure JVM unit testing, hexagonal core |
| `:app` | Kotlin, Jetpack Compose, Material 3 | Compose is Kotlin-only |
| DI | Hilt | Compile-time DI. There is no Spring on Android. |
| Persistence | Room + Kotlin Flow | |
| Number parsing | `com.googlecode.libphonenumber:libphonenumber` | Java lib, usable from `:core-domain` |
| Tests | JUnit 5 (core), JUnit 4 + Robolectric (app), Espresso (instrumented) | |
| `minSdk` | **29** (Android 10) | `RoleManager.ROLE_CALL_SCREENING` + `setSilenceCall` require it |
| `compileSdk` / `targetSdk` | latest stable | Verify current value before changing |

**Do not** add the libphonenumber `geocoder` or `carrier` artifacts. They add several MB to the
APK for information the app does not need. Country resolution comes from the core library alone.

**Language rule:** business logic goes in `:core-domain` as Java. Android framework code,
UI, and Room go in `:app` as Kotlin. Do not add Kotlin to `:core-domain`. Do not put
matching logic in `:app`.

---

## 3. Hard technical constraints (violating these breaks the app silently)

1. **`onScreenCall()` has a hard system timeout (~5 s).** No disk I/O, no `runBlocking`,
   no Room query on the calling thread. Rules must be served from an in-memory cache that
   is warmed on service bind. Persisting the `CallEvent` happens asynchronously *after*
   `respondToCall()` has already been invoked.
2. **`Call.Details.getHandle()` can be `null`** (withheld / private number). Never
   dereference it unguarded. This case maps to the reserved pattern token `PRIVATE`.
3. **`respondToCall()` must be called exactly once** for every `onScreenCall()`. On any
   exception in the decision path, fall through to *allow the call*. A crashed screener that
   silently swallows a real call is the worst possible failure mode. Wrap the whole body in
   try/catch and default to `CallResponse.Builder().build()` (= allow).
4. **Only one app can hold `ROLE_CALL_SCREENING`.** Acquiring it kicks out the previous
   holder (often Google Phone's spam filter). The UI must state this explicitly during
   onboarding.
5. `setSilenceCall(true)` is API 29+ and is **independent of** `setDisallowCall`. The
   `rejectCall` / `skipCallLog` / `skipNotification` flags only take effect when
   `setDisallowCall(true)` is also set.
6. The service must declare `android:permission="android.permission.BIND_SCREENING_SERVICE"`
   and the `android.telecom.CallScreeningService` intent filter, and be `android:exported="true"`.
7. `@AndroidEntryPoint` works on `Service` subclasses — use it, do not hand-roll a
   service locator.

---

## 4. Pattern grammar (v1 — do not extend without an ADR)

```
+43*            prefix match, all Austrian numbers
+43663*         prefix match, one operator range
+436631234567   exact match
*               matches every number
PRIVATE         reserved token: withheld / unknown caller id (handle == null)
```

- Patterns are matched against the **normalized E.164** form of the number, never the raw string.
- Exactly one trailing `*`, and only at the end. No regex, no `?`, no infix wildcards.
- A pattern without `*` must be a syntactically valid E.164 number.
- Validation lives in `core-domain` (`PatternSyntax`), and the UI must reuse it — never
  duplicate validation in Compose.

**Conflict resolution (implement exactly this, and unit-test it):**

1. Longest matching prefix wins (an exact match is the longest possible prefix).
2. On equal specificity: `ALLOW` > `SILENCE` > `BLOCK`.
3. No match → allow the call, record nothing.

---

## 5. Actions

Three actions. Code is English, UI is German (`strings.xml`, `values-de`), and they must not
drift apart.

| Domain enum | UI (de) | UI (en) | `CallResponse` |
|---|---|---|---|
| `BLOCK` | Sperren | Block | `disallowCall=true, rejectCall=true` |
| `SILENCE` | Lautlos | Silence | `silenceCall=true`, everything else false |
| `ALLOW` | Zulassen | Allow | default response, nothing set |

`SILENCE` means: the phone does not ring, the call still appears in the system call log, and it
falls through to voicemail if the caller waits. It is the safe default for country-wide rules —
recommend it in the UI when a user creates a `+XX*` rule.

---

## 6. Country resolution (the part that bites)

**Country → calling code is 1:1. Calling code → country is 1:n.** This asymmetry is not an edge
case, it is the common case:

| Code | Regions |
|---|---|
| `+1` | US, CA, and ~20 Caribbean territories |
| `+7` | RU **and** KZ |
| `+44` | GB, JE, GG, IM |
| `+39` | IT, VA |
| `+43` | AT only |

Consequences that must be implemented, not commented away:

- When a rule is created from the country picker, show the resulting pattern **and** every
  other region it will also block. „+1* sperrt auch Kanada und 20 weitere Gebiete."
- When a rule is *displayed*, resolve the pattern back to a country only if the mapping is
  unambiguous. Otherwise show the code and the region count.
- `CountryCatalog` lives in `:core-domain` and wraps `PhoneNumberUtil.getCountryCodeForRegion`
  and `getRegionCodesForCountryCode`. Verify the latter exists in the pinned libphonenumber
  version before relying on it; if not, `getRegionCodeForCountryCode` returns only the main
  region and the ambiguity must be derived another way.
- Flag emoji are **computed**, not shipped as assets: `0x1F1E6 + (isoChar - 'A')` for both
  letters of the ISO-3166 alpha-2 code. No image files, no network, works offline.
- Country display names come from `Locale.of("", iso2).getDisplayCountry(userLocale)`.
  Do not hardcode a country list.

---

## 7. Model and orchestration policy

The main session runs on **Opus**. Architecture, module boundaries, the screening hot path,
conflict resolution, and any ADR are Opus work. Well-scoped implementation is delegated to
**Sonnet** subagents.

Configured in `.claude/settings.json` (`"model": "opusplan"`) and in the subagent frontmatter
under `.claude/agents/`. Do not override this with `CLAUDE_CODE_SUBAGENT_MODEL` — it forces one
model on every subagent and defeats the split.

| Agent | Model | Tools |
|---|---|---|
| main session | Opus | all |
| `architect` | Opus | read-only |
| `implementer` | Sonnet | read, write, bash |
| `test-writer` | Sonnet | read, write, bash |
| `reviewer` | Opus | read-only |

### When to parallelise, and when not to

A subagent is an **isolated context window, not a background thread.** Nebenläufigkeit only
happens when several agents are dispatched in a single turn.

**Parallelise** independent work in the same milestone — three Compose screens, several
unrelated domain value objects, docs plus tests. Dispatch them together in one turn.

**Never parallelise** across a real dependency. `:core-domain` must compile before `:app` code
that consumes it. M1 precedes M2 and M3. A parallel agent that "helpfully" stubs a domain class
so its own branch compiles has just created a merge conflict with the agent writing the real one.

**The Gradle trap:** two agents running `./gradlew` in the same working directory will fight over
the build lock and the daemon. Either give parallel writers `isolation: worktree`, or let exactly
one agent own the build and have the others hand back diffs. Do not run parallel builds in the
same checkout.

Rule of thumb: if two tasks would be two separate PRs, they can run in parallel. If they would
be two commits in one PR, they cannot.

---

## 8. Git workflow — Git Flow, strictly

- `main` — release only, tagged `vX.Y.Z`, protected.
- `develop` — integration branch, default branch for PRs.
- `feature/<issue-nr>-<kebab-slug>` — branched from `develop`, merged into `develop`.
- `release/x.y.z`, `hotfix/x.y.z` — as per Git Flow.

**Never commit directly to `main` or `develop`.** Never `git push --force` to either.

```bash
git checkout develop && git pull
git checkout -b feature/12-country-picker
# ... work, commit in small logical steps ...
gh pr create --base develop --fill
```

**Commits: Conventional Commits.** `feat:`, `fix:`, `refactor:`, `test:`, `docs:`,
`chore:`, `build:`, `ci:`. Scope optional: `feat(core-domain): ...`.
Subject in English, imperative, ≤ 72 chars. No emoji. No `Co-Authored-By` trailers,
no "Generated with Claude Code" footers.

Ask before: `git push`, `gh pr merge`, `gh release create`, anything touching
`.github/workflows/`.

---

## 9. Definition of Done for any feature branch

- [ ] `:core-domain` logic covered by JUnit 5 tests, including the edge cases in §3, §4 and §6
- [ ] `./gradlew build` and `./gradlew lint` pass
- [ ] No new permission in `AndroidManifest.xml` unless explicitly approved
- [ ] No `Log.*` call that contains a phone number
- [ ] User-visible strings in `strings.xml` (`values` = en, `values-de` = de) — never hardcoded
- [ ] `README.md` updated if behaviour or setup changed
- [ ] PR description explains the *why*, not the diff

---

## 10. Working style in this repo

- **Plan before you build.** For anything larger than a bugfix, propose the file-level plan
  and wait for approval.
- **Small commits.** One logical change each. Do not bundle a refactor with a feature.
- Prefer deleting code over adding a flag.
- Do not add a dependency without naming the alternative you rejected and why.
- Do not create `*_new.kt`, `*_v2.java`, or backup copies. Edit the file. Git is the backup.
- If a test fails, fix the code — never weaken the assertion to make it pass.
- If something in this file contradicts reality (an API changed, a version moved), say so
  instead of silently working around it, and propose the edit to `CLAUDE.md`.

---

## 11. Commands

```bash
./gradlew build                     # compile + unit tests + lint on all modules
./gradlew :core-domain:test         # fast feedback loop for domain logic
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:connectedAndroidTest # instrumented tests (needs device/emulator, API 29+)
./gradlew lint                      # Android lint
```

---

## 12. Release and deployment

Full procedure in `docs/RELEASE.md` (signing) and `docs/WSL-ADB.md` (device from WSL2). The rules
that matter while an agent is in the loop:

- **Never run `keytool`.** Never read `~/.gradle/gradle.properties`, `*.jks`, `*.p12` or
  `keystore.properties` — signing credentials are locked by permission rules in
  `.claude/settings.json`. If a build fails for want of credentials, **report it and stop**; do not
  work around it by generating a key or relaxing a rule.
- The `signingConfig` in `app/build.gradle.kts` must be **optional** (absent keystore → unsigned
  build), otherwise CI, which has no keystore, cannot even run `assembleDebug`.
- `isMinifyEnabled = true` is the point where a debug-clean app breaks. libphonenumber loads its
  metadata by resource name, which R8 cannot see — so **a release APK tested only as debug is
  untested.** Build release, install on a device, create a `+43*` rule, and confirm the country
  picker and normalisation still work.
- Debug and release signatures differ, so installing one over the other fails with
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE` — uninstall first. **Uninstalling revokes
  `ROLE_CALL_SCREENING`**, so onboarding must be repeated afterwards.
- An empty `adb devices` in WSL is almost never an adb fault; it is `ADB_SERVER_SOCKET` unset or a
  client/server version mismatch. Do **not** restart daemons — point the user at
  `scripts/adb-env.sh` and `docs/WSL-ADB.md`.
- You cannot run `usbipd`: it needs an elevated PowerShell on the Windows host.
- **Never echo a phone number from `adb logcat` into the conversation.**
- Deploy with `/deploy debug` or `/deploy release`.

---

## 13. Privacy posture (this is a feature, not a footnote)

The selling point of this app against every commercial spam blocker is: it cannot phone home.
Enforce it in code, not in prose:

- `AndroidManifest.xml` must contain zero network permissions. CI fails the build otherwise.
- Optional setting: store `sha256(number + device-local salt)` instead of the number in
  `call_events`. Statistics by country/rule still work; the raw number does not persist.
- `CallEvent` retention defaults to 90 days, user-configurable, enforced by a periodic purge.
