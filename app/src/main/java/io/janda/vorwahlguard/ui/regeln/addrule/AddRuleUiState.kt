package io.janda.vorwahlguard.ui.regeln.addrule

import io.janda.vorwahlguard.domain.model.RuleAction

/** The two ways to build a rule (design decision, CLAUDE.md §4/§6 — no third tab without an ADR). */
enum class AddRuleTab {
    COUNTRY,
    PREFIX,
}

/**
 * The CLAUDE.md §6 collateral warning for a selected country: the alphabetically-first other
 * region the resulting calling-code pattern also matches, plus how many more there are beyond
 * that one. `null` on [AddRuleUiState.collateral] means the calling code is unambiguous — no
 * warning to show.
 */
data class CollateralInfo(
    val firstOtherRegionName: String,
    val remainingOtherCount: Int,
)

/**
 * Single immutable draft state for `AddRuleSheet`. Both the "Land wählen" and "Vorwahl eingeben"
 * tabs write the same [patternText]/[selectedAction] pair — there is exactly one draft, not one
 * per tab.
 *
 * [patternMissingWildcard] is set only on the "Vorwahl eingeben" tab: a valid, wildcard-free
 * pattern whose digits exactly equal a known calling code (e.g. the user typed `+43` instead of
 * `+43*`) parses as an EXACT match on the literal number "+43", which no real incoming call can
 * ever have — a syntactically valid but permanently dead rule. This flags that footgun without
 * blocking the (still syntactically legal) save.
 */
data class AddRuleUiState(
    val tab: AddRuleTab = AddRuleTab.COUNTRY,
    val countryQuery: String = "",
    val countries: List<CountryUi> = emptyList(),
    val selectedCountry: CountryUi? = null,
    val collateral: CollateralInfo? = null,
    val patternText: String = "",
    val patternValid: Boolean = false,
    val patternMissingWildcard: Boolean = false,
    val recommendedAction: RuleAction = RuleAction.BLOCK,
    val selectedAction: RuleAction = RuleAction.BLOCK,
    val testInput: String = "",
    val testOutcome: TestOutcome? = null,
    val duplicate: Boolean = false,
    val saveEnabled: Boolean = false,
    val saved: Boolean = false,
)
