# 0012 — Protokoll shows region + action for hashed rows, never a rule lookup

## Status

Accepted

## Context

PROJECT.md §8 open question 4: "Pseudonymisation and the log screen. If numbers are hashed, the
log can no longer show the number. Show the matched pattern instead, or exclude the last N
events from hashing?" M4 needs an answer before `ProtokollScreen` can replace its placeholder.

ADR 0010 already shipped the schema this has to work with: `CallEventEntity.isHashed` is a
per-row flag: `numberOrHash` is either the raw E.164 number or `sha256(number + salt)` hex,
decided at write time and never rewritten retroactively. `regionCode` and `matchedRuleId` stay
plain on every row, hashed or not, specifically so statistics keep working (ADR 0010). The
domain `CallEvent` record deliberately does not carry `isHashed` — ADR 0010: "the domain
CallEvent never knows about hashing" — so this is a display decision, not a domain one.

Two options from the open question, plus a third this ADR adds:

1. **Exclude the last N events from hashing.** Rejected outright: it means storing plaintext
   numbers for recent events even with pseudonymisation switched on, which defeats the setting
   users turned on for exactly those numbers. Contradicts CLAUDE.md §12's framing of
   pseudonymisation as a real privacy control, not a cosmetic one.
2. **Resolve `matchedRuleId` and show the matched pattern instead of the number.** The literal
   reading of the open question. Rejected: `call_events` intentionally has no foreign key to
   `rules` ("events must outlive rule deletion", `CallEventEntity`'s own KDoc) so statistics
   survive a rule being edited or removed. A hashed row's `matchedRuleId` can point at a rule
   that no longer exists, was edited to a different pattern since, or was replaced by an
   unrelated rule that reused... — Room `Upsert` on a fresh id makes id collision impossible,
   but *editing* is still a future feature (issue #23 explicitly deferred it) that would make a
   live lookup show a pattern the call was never actually screened against. A "matched pattern"
   column that can silently drift from what was true at record time is worse than not showing
   it.
3. **Show `regionCode` + `action`, nothing derived from `matchedRuleId`.** Both are plain
   columns on every row, hashed or not, captured at record time and never revised after the
   fact — CLAUDE.md §6's own "+1* (22 Gebiete)"-style copy already treats a bare region/country
   as meaningful, ambiguity-safe information on its own.

## Decision

When `CallEventEntity.isHashed` is `true`, `ProtokollScreen` renders the row from `regionCode`
and `action` only (e.g. "🇦🇹 Österreich · Lautlos"), with no number and no attempt to resolve
`matchedRuleId` to a live rule for display. When `isHashed` is `false`, the row shows the raw
E.164 number instead.

Because `isHashed` and `regionCode` are Room-layer facts the domain `CallEvent` deliberately
does not carry (ADR 0010), the read path for this screen stays inside `:app`: a small mapper
reads `CallEventEntity` (via `CallEventDao.observeNewestFirst()`) directly into a UI row type,
the same way `RuleRowUiMapper` (issue #23) reads Room-adjacent state without promoting it into
`:core-domain`. `matchedRuleId` is read by the mapper only to the extent a future
statistics/filtering feature might need it — the issue #25 UI row does not carry it, since
nothing today displays or filters on it.

`PRIVATE`-token rows are unaffected: ADR 0010 already stores them raw (`isHashed = 0`)
regardless of the pseudonymisation setting, so they keep rendering as today's "🔒 Unterdrückte
Nummer" treatment.

## Consequences

- A hashed row shows strictly less identifying information than an unhashed one by design — a
  user who turns pseudonymisation on trades "which number called" for "which region/action",
  which is the whole point of the setting, not a regression to explain away.
- No join, no dangling-reference handling, no "rule was deleted" placeholder text to design or
  test — the mapper only ever reads columns that are stable for the row's lifetime.
- If a future issue wants a "matched pattern" column, it needs its own decision (e.g. snapshot
  the pattern text into `call_events` at record time, alongside `regionCode`/`matchedRuleId`,
  rather than resolving it live) — this ADR does not preclude that, it just rejects doing it via
  a live rule lookup from the log screen.
- Two rows with the same `regionCode`/`action` are visually indistinguishable once hashed, which
  is intentional (that is the anonymity the setting promises) but means a user cannot tell two
  different hashed callers from the same country apart in the log — only aggregate counts
  distinguish them, which is what ADR 0010 already optimised the hash construction for.

See PROJECT.md §8 (open question 4), CLAUDE.md §12, ADR 0010, `CallEventEntity`
(`app/src/main/java/io/janda/vorwahlguard/data/events/`).
