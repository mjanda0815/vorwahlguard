package io.janda.vorwahlguard.data.events

/**
 * Sentinel written to [CallEventEntity.regionCode] when a screened call's region could not be
 * resolved (`PhoneNumber.region()` returned `null`) — see
 * [io.janda.vorwahlguard.screening.VorwahlGuardScreeningService]. Promoted to a shared constant
 * so a DAO-level aggregate query (e.g. excluding it from a "top regions" projection) can assert
 * against the exact same value the hot path writes, instead of a duplicated string literal.
 */
internal const val UNKNOWN_REGION_CODE = "UNKNOWN"
