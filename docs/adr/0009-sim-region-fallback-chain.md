# 0009 — SIM-region fallback chain for number normalization

## Status

Accepted

## Context

`NumberNormalizer.normalize(String raw, String defaultRegion)` (M1) needs a `defaultRegion` to
interpret a national-format incoming number (e.g. a local Austrian number arriving without a
`+43` prefix). PROJECT.md §8 open question 2 left the source of that region unresolved:
`TelephonyManager.getSimCountryIso()` can be empty (dual SIM, eSIM, no SIM), and the spec asked
for a fallback chain — SIM → network → `Locale` — without settling the order or whether it
needs a permission.

This matters for M2 because `VorwahlGuardScreeningService` must warm this value at service
`onCreate()` (CLAUDE.md §3 rule 1 — no Binder IPC on the `onScreenCall()` calling thread), so
the chain has to be fixed before `SimRegionProvider` is written, not decided ad hoc by
whichever agent implements it.

Two things narrow the decision:

- Neither `TelephonyManager.getSimCountryIso()` nor `getNetworkCountryIso()` requires a runtime
  permission (both are `PackageManager.PERMISSION_GRANTED` by default for reading the ISO
  country code alone) — so this decision adds no new manifest permission.
- The stakes are lower than they first look: an incoming call's `Call.Details.getHandle()` is
  normally already E.164 (carriers deliver it that way in practice), and `defaultRegion` is
  only consulted for the national-format fallback path. A wrong region degrades one edge case,
  it does not break the common path.

## Decision

`SimRegionProvider` resolves the region once, at service warm time, in this order:

```
getSimCountryIso()           — SIM's home country; best predictor for a national-format number
  → if blank/unavailable:
getNetworkCountryIso()       — current cell network's country; covers no-SIM/eSIM-data cases
  → if blank/unavailable:
Locale.getDefault().country  — offline, always available, last resort
  → if still blank: null (LibPhoneNumberNormalizer treats a null/unrecognized region as
    "cannot normalize" → PhoneNumber.UNKNOWN, per M1's existing normalizer contract)
```

The result is uppercased to ISO-3166 alpha-2 and cached in a `@Volatile` field, refreshed only
at `onCreate()`/explicit `refresh()` — never read from `TelephonyManager` inside
`onScreenCall()`.

## Consequences

- SIM region wins over network region even though the network is more "live": a traveler whose
  SIM is Austrian but who is currently roaming on a German network still gets `AT` as the
  default — correct for interpreting *their own* incoming national-format numbers from home
  contacts, but could misinterpret a genuinely local national-format number while roaming. This
  is judged the better default because the app's whole premise is prefix rules the user set up
  for their home country; re-litigate if roaming misclassification turns out to matter in
  practice.
- The cached value goes stale until the next `refresh()` — a SIM swap or landing in a new
  country mid-session won't be picked up until the service process restarts or is re-warmed.
  Acceptable for v1: this only affects the national-format fallback path, not E.164 handles.
- No new manifest permission, so this does not touch the no-`INTERNET`/minimal-permission
  posture (CLAUDE.md §1, §12).
- If this fallback chain later proves wrong for real users (§8 Q2 was flagged as needing real
  device testing), it's a single-file change (`SimRegionProvider`) — no port signature changes
  needed, since `NumberNormalizer.normalize(raw, defaultRegion)` already treats the region as
  an opaque string.

See `PROJECT.md` §8 open question 2, `CLAUDE.md` §3 rule 1, M1's `NumberNormalizer`/
`LibPhoneNumberNormalizer` (`core-domain/src/main/java/io/janda/vorwahlguard/domain/port/out/NumberNormalizer.java`,
`.../service/LibPhoneNumberNormalizer.java`).
