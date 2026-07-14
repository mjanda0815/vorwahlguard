# 0014 — Opt-in logging of allowed calls with the allow reason (issue #59)

## Status

Accepted

## Context

CLAUDE.md §4 rule 3 and ADR 0006 fix the recording behaviour of v1: a call where no rule
fires is allowed and **nothing** is recorded, and a contact-bypassed call is likewise not
recorded — deliberately, to keep contact-derived signals out of the local statistics. The
consequence, already flagged in ADR 0006, is a visibility gap: the Protokoll answers
"what did VorwahlGuard *do*", not "what came in", and a user reconciling the two sees calls
that are simply absent.

Issue #59 asks to close that gap **optionally**: a setting (default off) that also records
allowed calls, including *why* they were allowed — because the caller is a saved contact
(ADR 0006 bypass) or because no blocking rule matched. Explicit `ALLOW`-rule matches were
always recorded (they count as matched decisions).

Three design questions had to be settled:

1. **Where the reason is decided.** The service in `:app` cannot tell a bypass-allow from a
   no-match-allow — `ScreenIncomingCallService.decide()` returns `ScreeningDecision.allow()`
   for both. Re-deriving the distinction in `:app` would move screening semantics out of the
   domain (CLAUDE.md §2 language rule).
2. **How reason-only rows fit a schema whose `matched_rule_id` was non-nullable by
   invariant** — the first real Room migration of the project.
3. **Whether the Übersicht statistics count the new rows.** The architect recommended
   excluding them (the hero metric "geprüfte Anrufe" would otherwise change meaning when the
   toggle flips). The user decided the opposite: **count every type, and show the
   distribution** — the dashboard gains a per-type breakdown (Gesperrt / Lautlos / Zugelassen
   per Regel / Kontakt / Keine Regel) with a segmented-bar visualization, and all aggregates
   except the top-rules list include reason-only rows.

## Decision

- New domain enum `DecisionReason { RULE_MATCH, CONTACT_BYPASS, NO_MATCH }`, carried by
  `ScreeningDecision` (invariant: `matchedRuleId != null` iff `RULE_MATCH`) and persisted on
  `CallEvent`. The bypass branch of `ScreenIncomingCallService` returns
  `ScreeningDecision.contactBypass()`; `RuleMatcher` is untouched.
- New `Settings.logAllowedCalls`, **default off**. The recording gate in
  `VorwahlGuardScreeningService` becomes `matched() || logAllowedCalls`, evaluated from the
  in-memory settings cache — hot-path invariants (CLAUDE.md §3) unchanged; recording stays
  fire-and-forget after `respondToCall()`.
- Room schema v2: `call_events.matched_rule_id` nullable, new `reason TEXT NOT NULL`
  column; v1 rows are backfilled `RULE_MATCH` (correct by construction — v1 only ever
  recorded rule matches). Table-recreate migration, no destructive fallback, covered by a
  `MigrationTestHelper` instrumented test.
- Dashboard: **all recorded rows count** in the hero counter, sparkline and top-regions; the
  top-rules list filters `matched_rule_id IS NOT NULL` (a NULL group would be meaningless —
  and the query needs the filter for correctness anyway). A new breakdown section shows the
  five-type distribution.
- Pseudonymisation and retention purge apply to reason-only rows exactly like to every other
  row — no special-casing in `RoomCallEventRecorder`.

## Consequences

- **This partially reverses ADR 0006's "contact-bypassed calls are not recorded".** With
  both toggles on (bypass + logging), a contact's number enters `call_events` — hashed only
  if pseudonymisation is *also* on. That is a deliberate, user-opt-in privacy expansion; the
  default-off setting preserves ADR 0006's behaviour for everyone who doesn't ask otherwise.
- The meaning of the hero metric "geprüfte Anrufe" now depends on the toggle: off, it counts
  rule actions (as shipped); on, it grows toward "all incoming calls". The breakdown section
  makes the composition visible, which is the user-chosen mitigation for the metric-shift
  concern.
- The `matched_rule_id`-is-never-null invariant is gone. Every future DAO aggregate that
  groups or joins on it must decide explicitly whether reason-only rows belong in the result
  (the top-rules query is the first example).
- The Protokoll's ALLOW filter now mixes explicit `ALLOW`-rule matches with bypass/no-match
  rows; the per-row reason caption ("Kontakt" / "Keine passende Regel") is what
  distinguishes them.
- Old Protokoll copy ("Gesperrte und stummgeschaltete Anrufe erscheinen hier") is wrong once
  the toggle exists; replaced by action-neutral copy.

See issue #59, ADR 0006, ADR 0012 (log display), ADR 0013 (dashboard top rules).
