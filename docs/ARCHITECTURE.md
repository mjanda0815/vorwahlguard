# Architecture

## Modules

```
vorwahlguard/
├── settings.gradle.kts
├── gradle/libs.versions.toml       ← single source of truth for versions
├── core-domain/                    ← Java 17, java-library plugin
│   └── src/main/java/io/janda/vorwahlguard/domain/
│       ├── model/                  PhoneNumber, Pattern, Rule, RuleAction, CallEvent, Country
│       ├── port/in/                ScreenIncomingCall
│       ├── port/out/               RuleRepository, CallEventRecorder, Clock
│       └── service/                RuleMatcher, ScreenIncomingCallService,
│                                   LibPhoneNumberNormalizer, LibPhoneNumberCountryCatalog
└── app/                            ← Kotlin, com.android.application
    └── src/main/java/io/janda/vorwahlguard/
        ├── screening/              VorwahlGuardScreeningService, CallResponseMapper
        ├── data/                   Room entities, DAOs, repository adapters
        ├── di/                     Hilt modules
        └── ui/                     Compose: uebersicht, regeln, protokoll, einstellungen
```

### Dependency direction

```
:app  ──depends on──►  :core-domain
:core-domain  ──depends on──►  (nothing but libphonenumber)
```

This is enforced, not merely intended: `:core-domain` uses the `java-library` plugin, so an
`import android.*` will not compile. That is the point. Do not "temporarily" convert it to an
Android library module.

`NumberNormalizer` and `CountryCatalog` are declared as ports but *implemented inside*
`:core-domain`, because libphonenumber is plain Java. They stay behind interfaces so tests can
substitute them and so a future replacement does not ripple into `:app`.

## Ports and adapters

| Port (in `:core-domain`) | Direction | Adapter |
|---|---|---|
| `ScreenIncomingCall` | driving | `VorwahlGuardScreeningService` (`:app`) |
| `RuleRepository` | driven | `CachedRuleRepository` over `RuleDao` (`:app`) |
| `CallEventRecorder` | driven | `RoomCallEventRecorder`, async (`:app`) |
| `Clock` | driven | `SystemClock` (`:app`) |
| `NumberNormalizer` | driven | `LibPhoneNumberNormalizer` (`:core-domain`) |
| `CountryCatalog` | driven | `LibPhoneNumberCountryCatalog` (`:core-domain`) |

`ScreenIncomingCallService` is a plain Java class with constructor injection. It has no
annotations. Hilt binds it in `:app`'s DI module via an `@Provides` method. The domain never
sees Dagger.

## The screening hot path

`onScreenCall()` runs on the main thread of a bound service, with a system-imposed deadline.
The budget shapes the design:

- **Rules live in memory.** `CachedRuleRepository` holds an immutable `List<Rule>` snapshot,
  built once in `VorwahlGuardScreeningService.onCreate()` and refreshed by observing the DAO.
  The service process may be started without the UI process ever having run — warm the cache
  in the service, not in `Application.onCreate()`.
- **Recording is fire-and-forget.** `respondToCall()` is invoked first; the `CallEvent` is
  handed to a background dispatcher afterwards. A slow write must never delay a response.
- **Failure means allow.** The entire `onScreenCall` body is wrapped so that any exception
  results in a permissive `CallResponse`. Log the exception without the number.

## Country resolution

`CountryCatalog` exists because the mapping is asymmetric:

```
getCountryCodeForRegion("AT")     → 43            1:1, safe
getRegionCodesForCountryCode(1)   → [US, CA, …]   1:n, must be surfaced to the user
```

The picker walks `all()`, sorted by `Locale.getDisplayCountry` in the user's language. Flag emoji
are computed: two regional-indicator codepoints, `0x1F1E6 + (c - 'A')`. No assets, no network.

`describe(Pattern)` is a read model for the rule list: it returns the resolved regions and an
`ambiguous` flag, so the UI can render either a flag and a country name, or the bare code with a
territory count.

## Testing strategy

| Module | Framework | What is tested |
|---|---|---|
| `:core-domain` | JUnit 5, AssertJ | pattern parsing, normalization, conflict resolution, country ambiguity |
| `:app` (unit) | JUnit 4, Robolectric, MockK | `CallResponseMapper`, ViewModels, cache invalidation |
| `:app` (instrumented) | AndroidX Test, Room testing | Room migrations, DAO queries |
| `:app` (UI) | Compose UI test | rule creation via both input paths, validation feedback |

CI runs everything except instrumented tests. If a bug can be reproduced in `:core-domain`,
it *must* get a regression test there before it is fixed.

## Agent orchestration

`.claude/agents/` defines four roles. Opus designs and reviews, Sonnet implements. See
`CLAUDE.md` §7 for when work may be dispatched concurrently — and for the Gradle build-lock
trap that makes naive parallelism worse than sequential execution.

## Architecture Decision Records

Non-obvious decisions go in `docs/adr/NNNN-title.md` using the Nygard format
(Context / Decision / Consequences). Existing and planned:

- `0001` — Hexagonal split: Java domain, Kotlin Android shell
- `0002` — minSdk 29 rather than 24 (`RoleManager`, `setSilenceCall`)
- `0003` — No `INTERNET` permission, ever
- `0004` — Prefix grammar with a single trailing wildcard, no regex
- `0005` — Country codes resolve 1:n; the UI names the collateral before a rule is saved
- [`0006`](adr/0006-contacts-bypass-unconditional-priority.md) — Contacts-bypass has
  unconditional priority over explicit `BLOCK` rules
- ~~[`0007`](adr/0007-country-catalog-ambiguity-without-plural-api.md)~~ — superseded by `0008`;
  its premise (`getRegionCodesForCountryCode` absent) was wrong
- [`0008`](adr/0008-country-catalog-uses-plural-api-directly.md) — `CountryCatalog` wraps
  `getRegionCodesForCountryCode` directly, confirmed present via `javap` against the pinned jar
- *(open)* — Voicemail redirection: is `disallowCall && !rejectCall` carrier-dependent?
