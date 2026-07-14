package io.janda.vorwahlguard.ui.uebersicht

import java.time.LocalDate

/**
 * State for the Übersicht dashboard (issue #27). [loaded] distinguishes "not loaded yet" from
 * "loaded and genuinely empty" the same way [io.janda.vorwahlguard.ui.protokoll.ProtokollUiState]
 * and [io.janda.vorwahlguard.ui.regeln.RegelnUiState] do; [hasAnyEvents] further distinguishes
 * *why* the counter/sparkline/lists would be empty while loaded — `false` means nothing has ever
 * been recorded, so [UebersichtContent] shows the empty state instead of a zeroed-out dashboard.
 * [roleAvailable]/[roleHeld] are independent of [loaded] — they come from
 * [io.janda.vorwahlguard.screening.CallScreeningRoleProvider], not the event Flow, and are read
 * (and re-read on resume) regardless of whether any call has ever been screened.
 */
data class UebersichtUiState(
    val roleAvailable: Boolean = false,
    val roleHeld: Boolean = false,
    val totalScreened: Int = 0,
    val sparkline: List<DaySparkPoint> = emptyList(),
    val topCountries: List<TopCountryUi> = emptyList(),
    val topRules: List<TopRuleUi> = emptyList(),
    val actionBreakdown: List<BreakdownEntry> = emptyList(),
    val hasAnyEvents: Boolean = false,
    val loaded: Boolean = false,
)

/** One day's screened-call count, zero-filled for days with no activity — see [DashboardUiMapper.toSparkline]. */
data class DaySparkPoint(val date: LocalDate, val count: Int)

/** How a top-region aggregate is rendered, resolved once by [DashboardUiMapper] instead of in Compose. */
sealed interface TopCountryUi {
    data class Known(val flagEmoji: String, val name: String, val count: Int) : TopCountryUi
    data class Unknown(val regionCode: String, val count: Int) : TopCountryUi
}

/**
 * How a top-rule aggregate is rendered. [Deleted] covers a `matchedRuleId` that no longer
 * resolves against the live rule snapshot — `call_events` has no foreign key to `rules`
 * (`CallEventEntity`'s own KDoc: events must outlive rule deletion), so this is the expected
 * shape once a matched rule has since been removed, not an error state.
 */
sealed interface TopRuleUi {
    data class Known(val row: io.janda.vorwahlguard.ui.regeln.RuleRowUi, val count: Int) : TopRuleUi
    data class Deleted(val count: Int) : TopRuleUi
}

/**
 * The five categories the action/reason breakdown (issue #59) is bucketed into, in the fixed
 * display order [DashboardUiMapper.toBreakdown] always returns them in. `ALLOW_RULE` is an
 * explicit ALLOW rule match; `CONTACT` and `NO_RULE` only ever appear when
 * [io.janda.vorwahlguard.domain.model.Settings.logAllowedCalls] was on for at least one recorded
 * call.
 */
enum class BreakdownCategory { BLOCK, SILENCE, ALLOW_RULE, CONTACT, NO_RULE }

/** One category's call count for the Übersicht action breakdown — see [BreakdownCategory]. */
data class BreakdownEntry(val category: BreakdownCategory, val count: Int)
