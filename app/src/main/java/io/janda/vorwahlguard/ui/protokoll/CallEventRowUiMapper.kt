package io.janda.vorwahlguard.ui.protokoll

import io.janda.vorwahlguard.data.events.CallEventEntity
import io.janda.vorwahlguard.domain.model.DecisionReason
import io.janda.vorwahlguard.domain.model.PatternSyntax
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

        val timestampText = formatter(locale, zone).format(entity.occurredAt)

        val reason = runCatching { DecisionReason.valueOf(entity.reason) }.getOrNull()
        val allowReason = when (reason) {
            DecisionReason.CONTACT_BYPASS -> AllowReasonUi.CONTACT_BYPASS
            DecisionReason.NO_MATCH -> AllowReasonUi.NO_MATCH
            DecisionReason.RULE_MATCH, null -> null
        }

        // The pattern a "create rule from this row" tap would use (issue #76). A real number
        // maps to an EXACT rule for its E.164; a withheld caller to the PRIVATE token. Hashed
        // rows expose no number, so no rule can be built — leaving it null makes the row
        // non-actionable rather than fabricating a rule from a hash (CLAUDE.md §12). Validated
        // through PatternSyntax so a malformed stored value degrades to null, never a bad rule.
        val rulePattern = when (display) {
            is CallEventDisplay.Number ->
                display.e164.takeIf { PatternSyntax.isValid(it) }
            CallEventDisplay.Private -> PatternSyntax.PRIVATE_TOKEN
            is CallEventDisplay.Region, is CallEventDisplay.UnknownRegion -> null
        }

        return CallEventRowUi(entity.id, timestampText, action, display, allowReason, rulePattern)
    }

    // The formatter is expensive to build; ProtokollViewModel calls toRow() once per row on every
    // Flow emission, all on a single dispatcher, so a plain (non-synchronized) memo keyed by
    // locale+zone is safe and rebuilds only when either changes.
    private var cachedLocale: Locale? = null
    private var cachedZone: ZoneId? = null
    private var cachedFormatter: DateTimeFormatter? = null

    private fun formatter(locale: Locale, zone: ZoneId): DateTimeFormatter {
        val current = cachedFormatter
        if (current != null && locale == cachedLocale && zone == cachedZone) {
            return current
        }
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(locale)
            .withZone(zone)
            .also {
                cachedFormatter = it
                cachedLocale = locale
                cachedZone = zone
            }
    }
}
