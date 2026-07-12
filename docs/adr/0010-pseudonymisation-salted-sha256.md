# 0010 — Pseudonymisation: salted SHA-256 with a DataStore-resident salt

## Status

Accepted

## Context

CLAUDE.md §12 specifies an optional setting to store `sha256(number + device-local salt)`
instead of the raw number in `call_events`, with statistics by country and rule still working.
M3 makes `call_events` real (Room), so the write path has to decide *now* how the hash is
computed, where the salt lives, and what happens when the user flips the toggle after rows
already exist — retrofitting any of these onto a populated table would be worse.

Constraints that shape the decision:

- Statistics must survive hashing → `region_code` and `matched_rule_id` stay plain columns;
  only the number itself is pseudonymised. Identical callers must hash identically so grouping
  still works, which rules out per-row salts.
- The service's `"PRIVATE"` placeholder (withheld caller ID) is a constant token — hashing it
  adds nothing and would only obscure the log screen's "private number" rendering.
- The toggle will be flipped mid-life. Rows written under the old setting must stay readable.

## Decision

- **Hash:** `sha256(e164 + salt)`, UTF-8, lowercase hex — the CLAUDE.md §12 construction
  verbatim. Input is the exact E.164 string that would otherwise be stored, so equal callers
  produce equal hashes and per-caller grouping keeps working.
- **Salt:** 32 bytes from `SecureRandom`, Base64-encoded, stored in the settings Preferences
  DataStore under `pseudonym_salt` — a key the settings UI never surfaces. It is created
  lazily inside a single `datastore.edit { if absent, set }` transaction, so two concurrent
  first-writers cannot race two different salts, and memory-cached afterwards
  (`PseudonymSaltProvider`).
- **Per-row flag:** `call_events.is_hashed` records how each row was written. Mixed tables are
  always readable; toggling never migrates anything implicitly.
- **Placeholders bypass hashing:** values not starting with `+` (the `PRIVATE` token) are
  stored raw with `is_hashed = 0` even when pseudonymisation is on.
- **Decision point:** inside `RoomCallEventRecorder`'s background coroutine, reading
  `SettingsStore` directly (authoritative, and I/O is legal off the hot path). The domain
  `CallEvent` never knows about hashing.

**Rejected: Android-Keystore-backed HMAC key.** Stronger on paper (non-exportable key), but
Keystore keys can be invalidated by credential changes, which would orphan every existing hash
and break caller grouping permanently; and §12 specifies the salted-SHA-256 construction
explicitly. The simpler scheme is also auditable with `sqlite3` and a shell, which fits the
"verify it yourself" posture.

## Consequences

- `allowBackup` backs up the salt and the database together, so backup extraction defeats
  pseudonymisation exactly as much as it would defeat raw storage. The setting protects
  against on-device database inspection, not backup exfiltration — the M4 settings UI copy
  must not overclaim.
- Toggle ON → only new rows are hashed. A one-shot retroactive rewrite of old raw rows
  (`WHERE is_hashed = 0`) ships with the M4 toggle UI; the flag makes it trivial.
- Toggle OFF → new rows raw; already-hashed rows stay hashed forever. Irreversible by design —
  the M4 UI copy must say so.
- A rainbow-table attacker with device access reads the salt next to the hashes; this is
  pseudonymisation (GDPR sense), not encryption, and is documented as such.

See CLAUDE.md §12, `RoomCallEventRecorder`, `PseudonymSaltProvider`, `NumberPseudonymiser`
(`app/src/main/java/io/janda/vorwahlguard/data/events/`).
