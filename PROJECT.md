# PROJECT.md — VorwahlGuard

Functional specification. `CLAUDE.md` holds the rules of engagement; this file holds *what*
we are building. Keep both in sync.

---

## 1. Problem

Spam call campaigns rotate their caller ID on every attempt. Blocking a single number
accomplishes nothing — the next call arrives from a different one, and the display name is
faked anyway (Rufnummern-Spoofing; the BNetzA files it under „Manipulation von Rufnummern").
Android's built-in blocklist only accepts exact numbers.

What stays roughly constant across a campaign is the **prefix**: the country calling code, often
the operator range.

## 2. Solution

A `CallScreeningService` that evaluates every incoming call against user-defined prefix rules —
created either by picking a country from a list, or by typing a prefix directly — entirely
on-device, with a local statistics view showing what it caught.

## 3. Scope

### In scope (v1.0)

- Rules from **two input paths**: country picker (`Österreich` → `+43*`) and free-text prefix
  (`+43663*`, `+436631234567`)
- Three actions: `BLOCK` (Sperren), `SILENCE` (Lautlos), `ALLOW` (Zulassen)
- Reserved token `PRIVATE` for withheld caller IDs
- Rule list showing country **and** prefix side by side, with flag emoji
- Ambiguity warning when a country code maps to several regions (`+1` → US, CA, +20)
- Kontakt-Bypass: opt-in setting — calls from numbers stored in the device's contacts always
  get through, regardless of any blacklist rule. Requires `READ_CONTACTS`; contact data is
  read on-device only for a yes/no lookup, never persisted, never logged (see §12 privacy
  posture in `CLAUDE.md`).
- Whitelist: individual numbers can be exempted from the blacklist independently of any
  prefix rule. Technically just a `Rule` with `RuleAction.ALLOW` and an `EXACT` pattern — the
  existing conflict resolution (longest prefix wins, CLAUDE.md §4) already makes an exact
  match beat any shorter `BLOCK`/`SILENCE` prefix, so no new matching algorithm is needed.
  The UI groups rules by action so this reads as a distinct "Whitelist" section (see §7).
- Call event log with timestamp, matched rule, action taken, derived country
- Dashboard: total screened, last 7/30 days, top countries, top rules
- Onboarding flow for `ROLE_CALL_SCREENING`
- Settings: notification on block, retention period, number pseudonymisation
- Light/dark theme, German + English localisation

### Out of scope (v1.0)

- Call redirection to voicemail (see §8, open question)
- Cloud sync, shared blocklists, community lists (would require `INTERNET`)
- SMS filtering
- Operator/carrier name lookup — requires the libphonenumber `carrier` artifact, several MB
- Per-contact rule overrides or contact groups — v1.0 ships only the global
  "Kontakte immer durchlassen" toggle, not per-contact allow/block choices
- Play Store publishing (see §9)

---

## 4. Domain model (`:core-domain`, pure Java, no Android imports)

```
PhoneNumber        value object; raw + normalized E.164 + region code; may be UNKNOWN
Pattern            value object; parsed from string; kind = EXACT | PREFIX | ANY | PRIVATE
Rule               id, Pattern, RuleAction, enabled, label, createdAt
RuleAction         enum { BLOCK, SILENCE, ALLOW }
ScreeningDecision  action + matchedRuleId (nullable) ; ALLOW with null rule = "no match"
CallEvent          id, occurredAt, numberOrHash, regionCode, matchedRuleId, action
Country            iso2 + callingCode
PatternDescription pattern + resolved regions + ambiguous flag   (read model for the UI)
Settings           contactsBypassEnabled, retentionDays, pseudonymiseNumbers, notifyOnBlock
```

### Ports

```java
// driving (inbound)
public interface ScreenIncomingCall {
    ScreeningDecision decide(PhoneNumber number, boolean isKnownContact, Instant at);
}

// driven (outbound)
public interface RuleRepository    { List<Rule> activeRules(); }
public interface CallEventRecorder { void record(CallEvent event); }
public interface Clock             { Instant now(); }
public interface SettingsRepository { Settings current(); }
// contact membership is a plain boolean lookup — the adapter never hands a raw
// contact list into core-domain, only the yes/no answer for one number
public interface ContactsLookup    { boolean isKnownContact(PhoneNumber number); }

// pure, implemented inside core-domain (libphonenumber is plain Java)
public interface NumberNormalizer  { PhoneNumber normalize(String raw, String defaultRegion); }
public interface CountryCatalog {
    List<Country> all();                          // for the picker
    Optional<Country> byIso2(String iso2);        // "AT" -> +43
    List<String>  regionsFor(int callingCode);    // 1 -> [US, CA, BS, BB, ...]
    PatternDescription describe(Pattern pattern); // for the rule list
}
```

### Adapters (`:app`, Kotlin)

| Port | Adapter |
|---|---|
| `ScreenIncomingCall` | called from `VorwahlGuardScreeningService.onScreenCall()` |
| `RuleRepository` | `CachedRuleRepository` → in-memory snapshot, invalidated on Room change |
| `CallEventRecorder` | `RoomCallEventRecorder`, enqueued on a background dispatcher |
| `Clock` | `SystemClock` |
| `SettingsRepository` | `DataStoreSettingsRepository`, cached like the rule set |
| `ContactsLookup` | `ContactsProviderLookup` over `ContactsContract`, only queried when `contactsBypassEnabled` is true; no-op (always `false`) if `READ_CONTACTS` was never granted |

---

## 5. Screening flow

```
onScreenCall(details)
  ├─ handle == null ────────────► PhoneNumber.UNKNOWN
  ├─ else normalize(handle, simRegion)
  ├─ isContact = settings.contactsBypassEnabled && contactsLookup.isKnownContact(number)
  │              [in-memory; contacts are not re-queried per call, see open question #6 in §8]
  ├─ decision = screenIncomingCall.decide(number, isContact, now)   [in-memory, < 1 ms]
  ├─ respondToCall(call, toCallResponse(decision))       [ALWAYS, exactly once]
  └─ if decision.matched → recorder.record(...)          [async, after respond]
```

Whitelist entries need no special-case in this flow: an `EXACT` `ALLOW` rule is already the
longest possible prefix, so the standard conflict resolution (CLAUDE.md §4) picks it over any
shorter `BLOCK`/`SILENCE` prefix rule for the same number.

Mapping `ScreeningDecision` → `CallResponse`:

| Action | disallowCall | rejectCall | silenceCall | skipCallLog | skipNotification |
|---|---|---|---|---|---|
| `BLOCK` | `true` | `true` | `false` | `false` * | per setting |
| `SILENCE` | `false` | `false` | `true` | `false` | `false` |
| `ALLOW` | `false` | `false` | `false` | `false` | `false` |

\* Keep the system call log entry by default — a user must be able to verify what was
blocked outside the app. Make it a setting, default off.

**BLOCK vs. SILENCE, in user terms:** Sperren beendet den Anruf sofort (der Anrufer hört ein
Besetzt- oder Ablehnsignal). Lautlos lässt den Anruf durchlaufen, ohne dass das Telefon
klingelt — er landet im Anrufprotokoll und nach Timeout auf der Mailbox. Für eine ganze
Ländervorwahl ist Lautlos die risikoärmere Wahl; die UI schlägt sie bei `+XX*`-Regeln vor.

---

## 6. Country resolution

Country → calling code is 1:1. Calling code → country is **1:n**. See `CLAUDE.md` §6 for the
table and the implementation rules. Two user-visible consequences:

**Creating a rule from the picker.** After selecting a country, show the pattern that will be
created and, if the calling code is shared, name the collateral:

```
🇺🇸 Vereinigte Staaten  →  +1*
⚠ Diese Regel sperrt auch Kanada und 20 weitere Gebiete mit der Vorwahl +1.
```

**Displaying a rule.** Resolve back to a country only when unambiguous:

```
🇦🇹 Österreich          +43*          Lautlos
🇦🇹 Österreich          +43 663*      Sperren
   +1* (22 Gebiete)     +1*           Lautlos
🔒 Unterdrückte Nummer  PRIVATE       Lautlos
                        +49 30 …4711  Zulassen
```

Flag emoji are computed from the ISO code, not shipped as assets. Country names come from
`Locale`. Both work offline and follow the system language.

---

## 7. UI (Compose, Material 3)

Four destinations in a bottom navigation bar:

1. **Übersicht** — hero counter, 30-day sparkline, top 3 countries, top 3 rules. Prominent
   warning card if `ROLE_CALL_SCREENING` is not held.
2. **Regeln** — list grouped by action, showing country + prefix + action. Rules with action
   `Zulassen` and an exact pattern are visually grouped as **Whitelist**, everything with
   `Sperren`/`Lautlos` as **Blacklist** — same underlying `Rule` list, no separate storage.
   Swipe to delete, FAB to add. The add sheet has a segmented control:
   **Land wählen** | **Vorwahl eingeben**.
   - *Land wählen*: searchable country list, flag + name + calling code.
   - *Vorwahl eingeben*: free text with live validation against `PatternSyntax` and a
     "Nummer testen" field that runs a number through the current rule set.
   Both paths converge on the same action picker: Sperren / Lautlos / Zulassen.
3. **Protokoll** — reverse-chronological call events, filterable by action, with an empty state.
4. **Einstellungen** — role status, notifications, retention, pseudonymisation, about/licenses,
   and **Kontakte immer durchlassen** (opt-in, requests `READ_CONTACTS` on first enable, with
   copy explaining the lookup never leaves the device and is never logged).

Keep it small and quiet. No onboarding carousel, no dashboard gamification.

---

## 8. Open questions (resolve with an ADR in `docs/adr/`, do not guess)

1. **Voicemail redirection.** Does `disallowCall = true, rejectCall = false` route to the
   carrier voicemail, or drop the call silently? Undocumented and likely carrier-dependent.
   Needs an empirical test on a real device before it can be offered as a fourth action.
2. **SIM region detection.** `TelephonyManager.getSimCountryIso()` may be empty (dual SIM,
   eSIM, no SIM). Fallback chain: SIM → network → `Locale`. Which wins?
3. **Rule cache invalidation** across process death — the screening service can be bound in a
   process where the UI never ran. Warm the cache in `onCreate()` of the service, not the app.
4. **Pseudonymisation and the log screen.** If numbers are hashed, the log can no longer show
   the number. Show the matched pattern instead, or exclude the last N events from hashing?
5. **`getRegionCodesForCountryCode` availability** in the pinned libphonenumber version. If it
   is absent, how is ambiguity derived — a static table, or `getSupportedRegions()` filtered by
   `getCountryCodeForRegion`?
6. **Kontakt-Bypass vs. explizite Sperren-Regel.** If a number is both a saved contact and
   matches an explicit `BLOCK` rule, does the contact bypass win unconditionally, or should an
   explicit rule for that exact number be able to override it? Default assumption: contacts
   win outright (nobody wants grandma blocked because her number matches a country-wide
   prefix rule) — but this changes the decision function beyond the pure `Rule` conflict
   resolution in CLAUDE.md §4, so lock it down with an ADR before `ContactsLookup` ships.

---

## 9. Distribution

Google Play classifies call-log/phone functionality as a restricted permission group and
requires a declaration form; call screening apps are an accepted use case but the review is
non-trivial. **v1.0 ships as a GitHub Release APK.** Play Store is a separate decision, not a
v1.0 blocker. Consider F-Droid — the no-network, no-tracker posture is a natural fit.

---

## 10. Milestones

### M0 — Skeleton
Gradle multi-module setup, version catalog, `:core-domain` + `:app`, Hilt, Compose scaffold
with the four empty destinations, CI green, README committed. **Sequential, Opus plans.**

### M1 — Domain core
`PhoneNumber`, `Pattern` parsing + validation, `RuleMatcher` with conflict resolution,
`CountryCatalog` with the 1:n ambiguity, `ScreenIncomingCallService`. Fully unit-tested on the
JVM. **No Android code in this milestone.** Value objects can be parallelised across Sonnet
agents; `RuleMatcher` cannot — it depends on all of them.

### M2 — Screening service
`VorwahlGuardScreeningService`, `RoleManager` onboarding, in-memory rule cache, manifest
wiring, the `BLOCK`/`SILENCE`/`ALLOW` response mapping. `ContactsLookup` adapter and the
`READ_CONTACTS` request flow (only triggered when the setting is enabled) land here too —
resolve open question #6 in §8 with an ADR first. Manually verifiable: add `+43*` as
Lautlos, call from an Austrian number, the phone stays silent and the call appears in the log.

### M3 — Persistence
Room schema, `RuleRepository` and `CallEventRecorder` adapters, migrations, retention purge.

### M4 — UI
Rules CRUD with both input paths, country picker, Whitelist/Blacklist grouping in the Regeln
list, dashboard aggregates, log screen, settings (including the "Kontakte immer durchlassen"
toggle), theming, de/en strings. **The four screens are independent — parallelise them.**

### M5 — Hardening & release
Robolectric tests for the service, instrumented Room tests, accessibility pass, `v1.0.0` tag
and GitHub Release with a signed APK.
