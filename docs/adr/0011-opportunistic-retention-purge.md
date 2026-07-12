# 0011 — Retention purge runs opportunistically in-process, not via WorkManager

## Status

Accepted

## Context

CLAUDE.md §12: `CallEvent` retention defaults to 90 days, user-configurable, "enforced by a
periodic purge". The obvious Android answer is a WorkManager periodic worker — but WorkManager's
manifest merge introduces `RECEIVE_BOOT_COMPLETED` and `WAKE_LOCK`, and the Definition of Done
(CLAUDE.md §9) forbids new manifest permissions without explicit approval. Beyond the letter of
the rule, the app's audit story is "the manifest is short — read it yourself"; two scheduling
permissions for a housekeeping delete read badly in that manifest and invite exactly the
F-Droid-reviewer questions the privacy posture is meant to preempt.

The alternatives all hit the same wall: `JobScheduler` with `setPersisted(true)` requires
`RECEIVE_BOOT_COMPLETED` itself, and `AlarmManager` needs a boot receiver (same permission
again) to survive a reboot.

## Decision

One `RetentionPurger.purge()` — read `retentionDays` from `SettingsStore`, compute the cutoff
from the domain `Clock`, run a single indexed `DELETE FROM call_events WHERE occurred_at <
cutoff` — triggered opportunistically from the two places the process naturally wakes:

1. `VorwahlGuardScreeningService` warm-up, as a separate job after the cache-warm latch has
   opened (never ahead of screening readiness);
2. `VorwahlGuardApp.onCreate()`, launched on the application scope (UI opens → purge).

No scheduler, no receiver, no new permission.

## Consequences

- **Enforcement is event-driven, not clock-driven.** On a device that receives no calls and
  never opens the app, expired rows linger until the next natural wake-up. Accepted because
  the same idle device is accruing no new events, the data never leaves the device regardless,
  and every path that *creates* an event (an incoming call binds the service) also fires the
  purge — the table cannot grow unpurged.
- The purge shares the service process's lifetime headroom; the `DELETE` is served by the
  `occurred_at` index and runs on `Dispatchers.IO`, so it cannot delay `onScreenCall()`.
- "Periodic purge" in CLAUDE.md §12 is satisfied in effect (bounded staleness on any active
  device) but not by a literal periodic scheduler; if a true scheduler is ever wanted, that is
  a new ADR plus an explicitly approved manifest change.

See CLAUDE.md §9, §12, `RetentionPurger`
(`app/src/main/java/io/janda/vorwahlguard/data/events/RetentionPurger.kt`).
