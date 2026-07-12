# 0006 — Contacts-bypass has unconditional priority over explicit BLOCK rules

## Status

Accepted

## Context

The contacts-bypass setting (`PROJECT.md` §3, §5) lets calls from saved contacts through
regardless of blacklist rules. Implementing it changed the driving port's signature to
`ScreenIncomingCall.decide(PhoneNumber number, boolean isKnownContact, Instant at)`
(`PROJECT.md` §4) — `ScreenIncomingCallService` is M1 scope, so the priority between
`isKnownContact` and the `Rule` set has to be fixed before that class is written, not
deferred to M2 as originally flagged in `PROJECT.md` §8 open question 6.

Three places a contact could plausibly rank:

1. **Above everything** — a contact always gets through, even past an explicit `BLOCK` rule
   for that exact number.
2. **Below an explicit rule for the same number** — a user-created `EXACT` rule (whitelist or
   blacklist) for a number always wins; contacts-bypass only fills gaps left by prefix rules.
3. **As a `Rule`-equivalent, participating in the CLAUDE.md §4 conflict resolution** — treat
   contact membership as an implicit `ALLOW` rule with `EXACT` specificity and let the existing
   longest-prefix-wins logic decide.

Option 3 was rejected: it would require synthesizing a fake `Rule` per incoming call inside
the hot path, and it produces the same outcome as option 1 for the common case while being
harder to reason about and to unit test in isolation.

Option 2 was rejected as the default: it lets a broad `BLOCK` prefix rule (e.g. `+1*`) or a
mistaken explicit block accidentally silence a real contact, which is the exact failure mode
the setting exists to prevent. A user who genuinely wants to block a specific contact can
remove them from contacts or turn the global toggle off — a blunt instrument, but a safe one.

## Decision

`isKnownContact` is checked first, before the `Rule` set is consulted at all:

```
isKnownContact == true  → ScreeningDecision.allow(matchedRuleId = null)   [no Rule evaluated]
isKnownContact == false → RuleMatcher.match(rules, number)                [CLAUDE.md §4 as specified]
```

Contact-bypassed calls are **not recorded** as a `CallEvent`, consistent with the existing
"no match → record nothing" behaviour in CLAUDE.md §4 rule 3. This also avoids writing
contact-derived signals into the local statistics.

## Consequences

- A user cannot block one specific contact while keeping the bypass toggle on; the only lever
  is removing the contact or disabling the toggle globally. This is a real gap for a harassment
  scenario where the caller is a known contact — worth a per-contact override in v1.1, but out
  of scope for v1.0 (`PROJECT.md` §3 "Out of scope").
- Because bypassed calls are not recorded, the "total screened" dashboard metric and the
  Protokoll list will not reflect calls let through via contacts — a user comparing "calls I
  got" against "calls the app screened" will see a gap, and the UI copy must explain this
  (`PROJECT.md` §7 Einstellungen).
- The check is a single boolean branch ahead of `RuleMatcher`, so it costs nothing on the
  `onScreenCall` budget (CLAUDE.md §3 rule 1) — cheaper than option 3, not just simpler.
- If `READ_CONTACTS` is revoked after being granted, `ContactsLookup` must fail closed
  (`isKnownContact` → `false`), so revocation degrades to blacklist-only behaviour rather than
  silently allowing everything.

See `PROJECT.md` §4 (ports), §5 (screening flow), §8 open question 6.
