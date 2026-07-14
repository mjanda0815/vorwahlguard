package io.janda.vorwahlguard.ui.protokoll

import io.janda.vorwahlguard.data.events.CallEventEntity
import io.janda.vorwahlguard.domain.model.DecisionReason
import io.janda.vorwahlguard.domain.model.RuleAction
import io.janda.vorwahlguard.domain.port.out.CountryCatalog
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Builds [CallEventRowUi] from a [CallEventEntity] (ADR 0012). Mirrors
 * [io.janda.vorwahlguard.ui.regeln.RuleRowUiMapper]'s structure: a small stateless class the
 * owning `ViewModel` constructs itself, rather than something Hilt injects directly.
 */
class CallEventRowUiMapper(private val catalog: CountryCatalog) {

    /**
     * Returns `null` if [CallEventEntity.action] is no longer a valid [RuleAction] name — the
     * same lenient `runCatching { RuleAction.valueOf(...) }.getOrNull()` idiom [RuleEntity][
     * io.janda.vorwahlguard.data.rules.RuleEntity] uses, so a row degrades toward "not shown"
     * rather than crashing the log screen.
     */
    fun toRow(entity: CallEventEntity, locale: Locale, zone: ZoneId): CallEventRowUi? {
        val action = runCatching { RuleAction.valueOf(entity.action) }.getOrNull() ?: return null

        val display = if (entity.isHashed) {
            // ADR 0012: a hashed row shows region + action only — never numberOrHash (it is a
            // hash, not a number) and never a matchedRuleId lookup.
            val country = catalog.byIso2(entity.regionCode).orElse(null)
            if (country != null) {
                CallEventDisplay.Region(country.flagEmoji(), country.displayName(locale))
            } else {
                CallEventDisplay.UnknownRegion(entity.regionCode)
            }
        } else if (!entity.numberOrHash.startsWith("+")) {
            // Withheld calls are recorded with numberOrHash = "PRIVATE" and isHashed = false
            // (VorwahlGuardScreeningService/RoomCallEventRecorder: "PRIVATE" never starts with
            // '+', so it is never pseudonymised) — the only non-E.164 value this column holds.
            CallEventDisplay.Private
        } else {
            CallEventDisplay.Number(entity.numberOrHash)
        }

        val timestampText = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(locale)
            .withZone(zone)
            .format(entity.occurredAt)

        val reason = runCatching { DecisionReason.valueOf(entity.reason) }.getOrNull()
        val allowReason = when (reason) {
            DecisionReason.CONTACT_BYPASS -> AllowReasonUi.CONTACT_BYPASS
            DecisionReason.NO_MATCH -> AllowReasonUi.NO_MATCH
            DecisionReason.RULE_MATCH, null -> null
        }

        return CallEventRowUi(entity.id, timestampText, action, display, allowReason)
    }
}
