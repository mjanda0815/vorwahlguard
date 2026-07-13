package io.janda.vorwahlguard.ui.regeln

import io.janda.vorwahlguard.domain.model.RuleAction

/**
 * State for the Regeln list (issue #23). [RegelnViewModel] partitions the same underlying
 * [io.janda.vorwahlguard.domain.model.Rule] list into [whitelist]/[blacklist] via
 * [io.janda.vorwahlguard.domain.model.RuleSection] — there is exactly one `rules` table, this is
 * a display grouping only. [loaded] distinguishes "not loaded yet" from "loaded and genuinely
 * empty": the empty state is `loaded && whitelist.isEmpty() && blacklist.isEmpty()`.
 */
data class RegelnUiState(
    val whitelist: List<RuleRowUi> = emptyList(),
    val blacklist: List<RuleRowUi> = emptyList(),
    val loaded: Boolean = false,
)

/**
 * UI-ready projection of a single [io.janda.vorwahlguard.domain.model.Rule] row. Deliberately
 * does not carry a [io.janda.vorwahlguard.domain.model.RuleSection] — the caller (
 * [RegelnViewModel]) already sorted it into [RegelnUiState.whitelist]/[RegelnUiState.blacklist],
 * so which list a row is rendered in is the caller's decision, not this row's own state.
 */
data class RuleRowUi(
    val id: String,
    val patternText: String,
    val action: RuleAction,
    val label: RuleLabel,
)

/** How a rule's pattern is rendered, resolved once by [RuleRowUiMapper] instead of in Compose. */
sealed interface RuleLabel {
    data class Country(val flagEmoji: String, val name: String) : RuleLabel
    data class AmbiguousCode(val regionCount: Int) : RuleLabel
    data object Private : RuleLabel
    data object Raw : RuleLabel
}
