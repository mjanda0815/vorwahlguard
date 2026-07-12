# 0007 — CountryCatalog derives ambiguity via getSupportedRegions(), not getRegionCodesForCountryCode

## Status

Accepted

## Context

`CLAUDE.md` §6 specifies `CountryCatalog` as a wrapper over
`PhoneNumberUtil.getCountryCodeForRegion` and `getRegionCodesForCountryCode`, but flags that
the latter's existence must be verified in the pinned libphonenumber version
(`9.0.34`, `gradle/libs.versions.toml`) before relying on it, naming
`getRegionCodeForCountryCode` (singular — main region only) as the fallback signal.
`PROJECT.md` §8 open question 5 left open how ambiguity would then be derived: a hand-maintained
static table, or `getSupportedRegions()` filtered by calling code.

Checked against the `google/libphonenumber` source
(`java/libphonenumber/src/com/google/i18n/phonenumbers/PhoneNumberUtil.java`):

- `getRegionCodesForCountryCode` — **does not exist**. `CLAUDE.md` was right to flag this.
- `getRegionCodeForCountryCode(int)` — exists, returns a single `String` (the "main" region).
- `getSupportedRegions()` — exists, returns the full `Set<String>` of every region the library
  ships metadata for.
- `getCountryCodeForValidRegion(String)` — exists and returns `int`. Note: `CLAUDE.md` §6 names
  this `getCountryCodeForRegion`; that method name does not exist in the library. `CLAUDE.md`
  should be corrected separately (tracked outside this ADR).

A hand-maintained static table (calling code → region list) was considered and rejected: it
duplicates data that already ships inside libphonenumber's own metadata resources, would drift
out of sync silently on a library version bump, and reintroduces exactly the hardcoded country
list `CLAUDE.md` §6 forbids ("Do not hardcode a country list").

## Decision

`LibPhoneNumberCountryCatalog.regionsFor(int callingCode)` is implemented by filtering the full
supported-region set:

```java
phoneNumberUtil.getSupportedRegions().stream()
    .filter(region -> phoneNumberUtil.getCountryCodeForValidRegion(region) == callingCode)
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
- `CLAUDE.md` §6's reference to `getCountryCodeForRegion` is stale and must be corrected to
  `getCountryCodeForValidRegion` to avoid a future implementer chasing a method that does not
  exist.
- If libphonenumber ever renames `getSupportedRegions()` or `getCountryCodeForValidRegion()`,
  `CountryCatalog` fails to compile — loud, not silent, which is the safer failure mode for a
  version bump. Tests should pin the expected region count/members for `+1`, `+7`, `+44`, `+39`
  (`CLAUDE.md` §6 table) so a metadata-only change (library still compiles, but region lists
  shrink or grow) is also caught.

See `CLAUDE.md` §6, `PROJECT.md` §8 open question 5.
