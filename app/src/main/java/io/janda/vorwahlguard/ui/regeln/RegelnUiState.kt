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

    /**
     * A calling code that resolves to several regions (PROJECT.md §6, e.g. `+44*` → GB/GG/IM/JE):
     * the full, ISO-sorted list of affected [regions], each with its computed flag emoji and
     * localized name (issue #91). The list body shows the code + count as a header and the regions
     * below it, collapsing to the first few with a tap-to-expand affordance when there are many
     * (e.g. `+1*` → ~24). [regions] is never empty — an empty resolution maps to [Raw] instead.
     */
    data class AmbiguousCode(val regions: List<RegionEntry>) : RuleLabel

    data object Private : RuleLabel
    data object Raw : RuleLabel
}

/** One affected region of an [RuleLabel.AmbiguousCode] rule: its flag emoji and localized name. */
data class RegionEntry(val flagEmoji: String, val name: String)
