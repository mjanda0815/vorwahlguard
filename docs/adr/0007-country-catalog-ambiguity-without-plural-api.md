# 0007 — CountryCatalog derives ambiguity via getSupportedRegions(), not getRegionCodesForCountryCode

## Status

Superseded by [0008](0008-country-catalog-uses-plural-api-directly.md). Kept for the record:
two independent source-fetch attempts (wrong branch, then a truncated correct-tag fetch) both
failed to prove `getRegionCodesForCountryCode` was absent — it wasn't. See 0008 for the
`javap`-verified fact and the corrected decision.

## Context

`CLAUDE.md` §6 specifies `CountryCatalog` as a wrapper over
`PhoneNumberUtil.getCountryCodeForRegion` and `getRegionCodesForCountryCode`, but flags that
the latter's existence must be verified in the pinned libphonenumber version
(`9.0.34`, `gradle/libs.versions.toml`) before relying on it, naming
`getRegionCodeForCountryCode` (singular — main region only) as the fallback signal.
`PROJECT.md` §8 open question 5 left open how ambiguity would then be derived: a hand-maintained
static table, or `getSupportedRegions()` filtered by calling code.

Checked against the `google/libphonenumber` source at the pinned tag `v9.0.34`
(`java/libphonenumber/src/com/google/i18n/phonenumbers/PhoneNumberUtil.java`), and — more
reliably, since the source fetch is truncated and self-admittedly unreliable for proving a
method's *absence* — against this repository's own `SmokeTest.java`, which already calls
`PhoneNumberUtil.getInstance().getCountryCodeForRegion("AT")` and compiles green in CI on
`develop` (commit `bb80985`):

- `getCountryCodeForRegion(String)` — **confirmed present and public**, proven by the
  already-compiling `SmokeTest`. An earlier draft of this ADR wrongly claimed this method
  does not exist and proposed `getCountryCodeForValidRegion` instead, based on a source fetch
  against libphonenumber's `master` branch rather than the pinned `v9.0.34` tag. `CLAUDE.md`
  §6's reference to `getCountryCodeForRegion` was correct all along and needed no fix.
- `getRegionCodesForCountryCode` — not found in either source fetch (`master` or `v9.0.34`),
  consistent with `CLAUDE.md` §6's own hedge ("verify the latter exists ... if not ..."). This
  absence is **not yet proven by compilation** the way the point above is — only by a
  best-effort source search, which this ADR's own history shows can mislead. Treat it as a
  working assumption to be confirmed by an actual compile attempt once a JDK is available in
  the dev environment (attempt `phoneNumberUtil.getRegionCodesForCountryCode(1)` and expect a
  compile error before relying on its absence in `CountryCatalog`).
- `getSupportedRegions()` — exists, returns the full `Set<String>` of every region the library
  ships metadata for. Consistently found across both source fetches.

A hand-maintained static table (calling code → region list) was considered and rejected: it
duplicates data that already ships inside libphonenumber's own metadata resources, would drift
out of sync silently on a library version bump, and reintroduces exactly the hardcoded country
list `CLAUDE.md` §6 forbids ("Do not hardcode a country list").

## Decision

`LibPhoneNumberCountryCatalog.regionsFor(int callingCode)` is implemented by filtering the full
supported-region set with the proven-present `getCountryCodeForRegion`:

```java
phoneNumberUtil.getSupportedRegions().stream()
    .filter(region -> phoneNumberUtil.getCountryCodeForRegion(region) == callingCode)
    .sorted()
    .toList();
```

A calling-code → region-list map is built once (e.g. on first use or service init) and cached,
rather than re-scanning ~250 regions per call. This scan never runs on the `onScreenCall` hot
path (CLAUDE.md §3 rule 1) — `CountryCatalog` is only used by the rule picker and the rule
list's `describe(Pattern)` read model.

## Consequences

- No hardcoded country/region data in the app; stays correct across libphonenumber upgrades as
  long as the two method names remain stable.
- One-time O(supported regions) scan at cache-build time — acceptable outside the hot path, but
  the cache-build call site must be chosen deliberately (app start or first picker open, not
  per-keystroke in the picker).
- Before writing `LibPhoneNumberCountryCatalog`, confirm `getRegionCodesForCountryCode` is
  really absent by attempting to compile a call to it — a source-fetch "not found" is weaker
  evidence than a compiler error, as this ADR's own revision history demonstrates.
- If libphonenumber ever renames `getSupportedRegions()` or `getCountryCodeForRegion()`,
  `CountryCatalog` fails to compile — loud, not silent, which is the safer failure mode for a
  version bump. Tests should pin the expected region count/members for `+1`, `+7`, `+44`, `+39`
  (`CLAUDE.md` §6 table) so a metadata-only change (library still compiles, but region lists
  shrink or grow) is also caught.

See `CLAUDE.md` §6, `PROJECT.md` §8 open question 5.
