# 0008 — CountryCatalog wraps getRegionCodesForCountryCode directly

## Status

Accepted. Supersedes [0007](0007-country-catalog-ambiguity-without-plural-api.md).

## Context

ADR 0007 decided `CountryCatalog` must derive calling-code ambiguity by filtering
`getSupportedRegions()`, because `getRegionCodesForCountryCode` was believed absent from the
pinned libphonenumber `9.0.34`. That belief rested on two source-fetch attempts against
GitHub-hosted `.java` source (first the wrong branch, then the right tag but a truncated,
model-summarized read) — neither is proof of absence, only of "not found in what was read."

While implementing `LibPhoneNumberCountryCatalog` for M1, with a JDK now available in the dev
environment, the question was checked against the one source that cannot mislead: the actual
resolved dependency jar.

```
$ javap -classpath ~/.gradle/caches/modules-2/files-2.1/com.googlecode.libphonenumber/libphonenumber/9.0.34/.../libphonenumber-9.0.34.jar \
    com.google.i18n.phonenumbers.PhoneNumberUtil | grep -iE "getRegionCode|getCountryCodeFor|getSupportedRegions"

  public java.util.Set<java.lang.String> getSupportedRegions();
  public java.lang.String getRegionCodeForNumber(...);
  public java.lang.String getRegionCodeForCountryCode(int);
  public java.util.List<java.lang.String> getRegionCodesForCountryCode(int);
  public int getCountryCodeForRegion(java.lang.String);
```

`getRegionCodesForCountryCode(int)` is public and present, returning `List<String>` — exactly
the method `CLAUDE.md` §6 originally named. ADR 0007's premise was wrong, and its
`getSupportedRegions()`-filter workaround, while functionally valid, is unnecessary complexity.

## Decision

`LibPhoneNumberCountryCatalog.regionsFor(int callingCode)` calls
`phoneNumberUtil.getRegionCodesForCountryCode(callingCode)` directly. The result is still
cached per calling code rather than re-queried on every lookup (ADR 0007's caching rationale
stands: this must never run on the `onScreenCall` hot path, CLAUDE.md §3 rule 1), but the
lookup itself is a single direct call instead of a full scan-and-filter over every supported
region.

`CLAUDE.md` §6 is corrected to drop the "verify before relying on it" hedge — the verification
is done and the method is confirmed.

## Consequences

- Simpler, more obviously-correct implementation than ADR 0007's filter-based fallback.
- The process lesson is the more durable output of this ADR: three attempts (wrong-branch
  fetch, truncated-right-tag fetch, `javap` against the real jar) were needed to settle one
  method's existence. Once a JDK/Gradle cache is available, "does this API exist" questions in
  this repo should go straight to `javap` (or a throwaway compile) against the resolved
  dependency jar, not to browsing library source on GitHub — a source read can miss a method
  behind truncation or summarization with no signal that it did so, while a compiler or
  `javap` either finds the symbol or doesn't.
- If libphonenumber ever removes `getRegionCodesForCountryCode`, `CountryCatalog` fails to
  compile — loud, not silent. Tests already pin expected region membership for `+1`, `+7`,
  `+44`, `+39`, `+43` (`CLAUDE.md` §6 table), so a metadata-only change is also caught.

See `CLAUDE.md` §6, ADR 0007, `PROJECT.md` §8 open question 5 (now closed).
