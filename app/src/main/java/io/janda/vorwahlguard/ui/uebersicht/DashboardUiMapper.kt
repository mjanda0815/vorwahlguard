package io.janda.vorwahlguard.ui.uebersicht

import io.janda.vorwahlguard.data.events.RegionCount
import io.janda.vorwahlguard.data.events.RuleIdCount
import io.janda.vorwahlguard.domain.model.Rule
import io.janda.vorwahlguard.domain.port.out.CountryCatalog
import io.janda.vorwahlguard.ui.regeln.RuleRowUiMapper
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Builds the Übersicht dashboard's derived UI values from Room aggregates and the live rule
 * snapshot. Mirrors [io.janda.vorwahlguard.ui.protokoll.CallEventRowUiMapper]/[RuleRowUiMapper]'s
 * structure: a small stateless class the owning `ViewModel` constructs itself, rather than
 * something Hilt injects directly.
 */
class DashboardUiMapper(private val catalog: CountryCatalog) {

    /**
     * Builds 30 zero-filled daily buckets for the 30 days ending at [now] (inclusive of today),
     * chronological order (oldest first). [occurredAtMillis] values outside that window (there
     * should be none, given the caller queries with the same window, but a stray value would
     * otherwise silently vanish) are ignored rather than widening the returned list.
     */
    fun toSparkline(occurredAtMillis: List<Long>, now: Instant, zone: ZoneId): List<DaySparkPoint> {
        val today = now.atZone(zone).toLocalDate()
        val windowStart = today.minusDays(WINDOW_DAYS - 1L)

        val counts = occurredAtMillis
            .map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            .filter { !it.isBefore(windowStart) && !it.isAfter(today) }
            .groupingBy { it }
            .eachCount()

        return (0 until WINDOW_DAYS).map { offset ->
            val date = windowStart.plusDays(offset.toLong())
            DaySparkPoint(date, counts[date] ?: 0)
        }
    }

    /** [regionCount.regionCode] resolved to a known [io.janda.vorwahlguard.domain.model.Country], or [TopCountryUi.Unknown] if it does not resolve. */
    fun toTopCountry(regionCount: RegionCount, locale: Locale): TopCountryUi {
        val country = catalog.byIso2(regionCount.regionCode).orElse(null)
        return if (country != null) {
            TopCountryUi.Known(country.flagEmoji(), country.displayName(locale), regionCount.count)
        } else {
            TopCountryUi.Unknown(regionCount.regionCode, regionCount.count)
        }
    }

    /** Each [RuleIdCount.ruleId] resolved against [rulesById], or [TopRuleUi.Deleted] if the rule no longer exists. */
    fun toTopRules(
        counts: List<RuleIdCount>,
        rulesById: Map<String, Rule>,
        ruleRowMapper: RuleRowUiMapper,
        locale: Locale,
    ): List<TopRuleUi> = counts.map { ruleIdCount ->
        val rule = rulesById[ruleIdCount.ruleId]
        if (rule != null) {
            TopRuleUi.Known(ruleRowMapper.toRow(rule, locale), ruleIdCount.count)
        } else {
            TopRuleUi.Deleted(ruleIdCount.count)
        }
    }

    private companion object {
        const val WINDOW_DAYS = 30
    }
}
