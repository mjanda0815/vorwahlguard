package io.janda.vorwahlguard.ui.protokoll

import io.janda.vorwahlguard.domain.model.RuleAction

/**
 * State for the Protokoll list (issue #25). [loaded] distinguishes "not loaded yet" from "loaded"
 * — once `true`, an empty [events] is genuine, not a loading artifact. [hasAnyEvents] further
 * distinguishes *why* [events] is empty while loaded: `true` means the unfiltered event set is
 * non-empty and [selectedFilter] filtered it down to nothing, `false` means there is nothing
 * recorded at all yet. Mirrors [io.janda.vorwahlguard.ui.regeln.RegelnUiState]'s `loaded` idiom.
 */
data class ProtokollUiState(
    val events: List<CallEventRowUi> = emptyList(),
    val selectedFilter: RuleAction? = null,
    val hasAnyEvents: Boolean = false,
    val loaded: Boolean = false,
)

/** UI-ready projection of a single [io.janda.vorwahlguard.data.events.CallEventEntity] row. */
data class CallEventRowUi(
    val id: String,
    val timestampText: String,
    val action: RuleAction,
    val display: CallEventDisplay,
    /** Non-null only for a logged allowed call that did not come from a rule match (issue #59). */
    val allowReason: AllowReasonUi? = null,
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
