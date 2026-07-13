# 0013 — Übersicht's "top rules" resolves the live rule, not a snapshot

## Status

Accepted

## Context

Issue #27 (Übersicht dashboard) needs a "top 3 rules" list, grouped from `call_events.matched_rule_id`
by match count. This is the same shape of problem ADR 0012 already solved for the Protokoll log:
a stored id can point at a rule that has since been edited or deleted, because `call_events`
deliberately has no foreign key to `rules` (events must outlive rule changes).

ADR 0012's answer for the *log* was: never resolve `matched_rule_id` to a live rule for display,
show `regionCode` + `action` instead, because a log row's whole point is to say what was true
*when that call happened* — a live lookup would silently drift from that.

The dashboard's "top rules" is a different kind of question: not "what happened on this call" but
"which of my current rules are catching the most traffic, right now." Applying ADR 0012's answer
literally here would mean showing nothing more than an opaque count next to an id — useless for a
user deciding whether to tighten, loosen, or delete a rule, which is exactly the decision this
list exists to support.

## Decision

`Übersicht`'s top-rules list resolves each `matched_rule_id` against the *current* rule snapshot
(`RuleSnapshotSource.observe()`, the same source `RegelnViewModel` already uses) and renders a
match through the existing `RuleRowUiMapper` — a dashboard rule row looks identical to its row in
the Regeln list. An id with no current rule renders as a distinct "Deleted rule" placeholder,
still showing its real match count — it is not dropped, because the screening history behind that
count is real even if the rule that produced it is gone.

This is a deliberate, scoped exception to ADR 0012's "no live lookup" stance, not a reversal of
it: ADR 0012 governs a per-call log row (must reflect record-time truth), this ADR governs a
rule-centric aggregate (the live rule *is* the right referent — a user editing a rule wants the
dashboard to reflect the edit immediately, not show the pattern as it was when each historical
call matched).

## Consequences

- **Edited-rule drift is the intended behavior, not a bug to document defensively.** If a rule's
  pattern or action is edited after some calls matched it under the old definition, the dashboard
  row shows the *current* pattern/action next to a count that includes calls matched under the
  old one. This is correct for "what is this rule doing for me today," wrong for "what exactly
  happened on each of those calls" — which is precisely why the log (ADR 0012) and the dashboard
  answer the question differently on purpose.
- A deleted rule's historical match count survives as a labelled placeholder, with no way to see
  what pattern it used to be — accepted, since `call_events` intentionally carries no pattern
  snapshot (see ADR 0012's Consequences for the same trade-off, stated there for the log).
- Two ADRs now give two different answers to "what does `matched_rule_id` mean for display,"
  which is intentional but easy to misapply if a third screen ever wants this data — the deciding
  question for any future case is: *does this view describe one specific past call, or the
  current state of a rule?* Log-shaped views follow 0012; rule-shaped aggregates follow this one.
- Reusing `RuleRowUiMapper` here (rather than a bespoke renderer) means the dashboard's rule rows
  automatically pick up any future change to how a rule renders (e.g. a new `RuleLabel` variant)
  with zero Übersicht-specific code — the coupling is deliberate, not incidental duplication.

See PROJECT.md §7 point 1 (Übersicht), ADR 0012, `RuleRowUiMapper`
(`app/src/main/java/io/janda/vorwahlguard/ui/regeln/RuleRowUiMapper.kt`), `DashboardUiMapper`
(`app/src/main/java/io/janda/vorwahlguard/ui/uebersicht/DashboardUiMapper.kt`).
