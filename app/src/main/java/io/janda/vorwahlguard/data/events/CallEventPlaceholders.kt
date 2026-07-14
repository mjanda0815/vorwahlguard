package io.janda.vorwahlguard.data.events

/**
 * Sentinel written to [CallEventEntity.regionCode] when a screened call's region could not be
 * resolved (`PhoneNumber.region()` returned `null`) — see
 * [io.janda.vorwahlguard.screening.VorwahlGuardScreeningService]. Promoted to a shared constant
 * so a DAO-level aggregate query (e.g. excluding it from a "top regions" projection) can assert
 * against the exact same value the hot path writes, instead of a duplicated string literal.
 */
internal const val UNKNOWN_REGION_CODE = "UNKNOWN"

/**
 * Sentinel written to [CallEventEntity.numberOrHash] for a withheld caller ID
 * (`Call.Details.getHandle() == null`, CLAUDE.md §3 rule 2) — see
 * [io.janda.vorwahlguard.screening.VorwahlGuardScreeningService]. Shared here alongside
 * [UNKNOWN_REGION_CODE] for the same reason: it never starts with `+`, so [RoomCallEventRecorder]
 * stores it verbatim (never pseudonymised) and [io.janda.vorwahlguard.ui.protokoll.CallEventRowUiMapper]
 * renders it as a withheld call. It coincides in value — but not by reference — with
 * `PatternSyntax.PRIVATE_TOKEN`; the two are independent (a recorded event vs. a rule pattern).
 */
internal const val PRIVATE_NUMBER_PLACEHOLDER = "PRIVATE"
