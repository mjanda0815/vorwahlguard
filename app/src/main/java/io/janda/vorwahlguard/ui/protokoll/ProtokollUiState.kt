package io.janda.vorwahlguard.ui.protokoll

import io.janda.vorwahlguard.domain.model.RuleAction

/**
 * State for the Protokoll list (issue #25). [loaded] distinguishes "not loaded yet" from "loaded"
 * — once `true`, an empty [events] is genuine, not a loading artifact. [hasAnyEvents] further
 * distinguishes *why* [events] is empty while loaded: `true` means the unfiltered event set is
 * non-empty and [selectedFilter] filtered it down to nothing, `false` means there is nothing
 * recorded at all yet. Mirrors [io.janda.vorwahlguard.ui.regeln.RegelnUiState]'s `loaded` idiom.
 *
 * [pendingRule] holds the row the user tapped to create a rule from (issue #76): non-null drives
 * the action-picker dialog; `null` means no dialog.
 */
data class ProtokollUiState(
    val events: List<CallEventRowUi> = emptyList(),
    val selectedFilter: RuleAction? = null,
    val hasAnyEvents: Boolean = false,
    val loaded: Boolean = false,
    val pendingRule: PendingRule? = null,
)

/**
 * A rule the user is about to create from a tapped log row (issue #76): the canonical pattern
 * text to persist, and [numberLabel] — the E.164 number to show in the picker dialog, or `null`
 * for a withheld caller (the dialog then shows the "Unterdrückte Nummer" string resource). Never
 * a hash: pseudonymised rows are not actionable, so they never reach this state.
 */
data class PendingRule(val patternText: String, val numberLabel: String?)

/**
 * UI-ready projection of a single [io.janda.vorwahlguard.data.events.CallEventEntity] row.
 *
 * [rulePattern] is the canonical pattern text a rule for this caller would use, or `null` when no
 * rule can be built from the row (issue #76): a pseudonymised row exposes only a hash, so there is
 * no number to turn into an EXACT rule. Non-null for a real number (its E.164) and for a withheld
 * caller (the `PRIVATE` token).
 */
data class CallEventRowUi(
    val id: String,
    val timestampText: String,
    val action: RuleAction,
    val display: CallEventDisplay,
    /** Non-null only for a logged allowed call that did not come from a rule match (issue #59). */
    val allowReason: AllowReasonUi? = null,
    val rulePattern: String? = null,
)

/**
 * Why a logged allowed call was allowed, when it was not an explicit rule match (issue #59 /
 * ADR 0014). `null` on [CallEventRowUi.allowReason] covers both rule matches and non-ALLOW rows.
 */
enum class AllowReasonUi { CONTACT_BYPASS, NO_MATCH }

/**
 * How a logged call is rendered, resolved once by [CallEventRowUiMapper] instead of in Compose.
 * [Region]/[UnknownRegion] apply to hashed rows (ADR 0012: region + action only, never a number
 * and never a `matchedRuleId` lookup); [Number] applies to unhashed rows; [Private] applies to a
 * withheld caller ID regardless of the pseudonymisation setting.
 */
sealed interface CallEventDisplay {
    data class Number(val e164: String) : CallEventDisplay
    data class Region(val flagEmoji: String, val name: String) : CallEventDisplay
    data class UnknownRegion(val regionCode: String) : CallEventDisplay
    data object Private : CallEventDisplay
}
