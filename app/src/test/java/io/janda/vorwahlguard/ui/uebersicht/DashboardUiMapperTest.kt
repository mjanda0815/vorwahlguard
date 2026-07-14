package io.janda.vorwahlguard.ui.uebersicht

import io.janda.vorwahlguard.data.events.ActionReasonCount
import io.janda.vorwahlguard.data.events.RegionCount
import io.janda.vorwahlguard.data.events.RuleIdCount
import io.janda.vorwahlguard.domain.model.Country
import io.janda.vorwahlguard.domain.model.DecisionReason
import io.janda.vorwahlguard.domain.model.PatternSyntax
import io.janda.vorwahlguard.domain.model.Rule
import io.janda.vorwahlguard.domain.model.RuleAction
import io.janda.vorwahlguard.domain.port.out.CountryCatalog
import io.janda.vorwahlguard.ui.regeln.RuleRowUiMapper
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import java.util.Optional
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers [DashboardUiMapper]: [DashboardUiMapper.toSparkline]'s 30-bucket zero-filled window
 * (chronological order, per-day counts, exclusion of a stray out-of-window value, the exact local
 * midnight boundary), [DashboardUiMapper.toTopCountry]'s resolvable/unresolvable split, and
 * [DashboardUiMapper.toTopRules]'s known/deleted split with order preservation. No Robolectric —
 * a pure mapper, mirrors [io.janda.vorwahlguard.ui.protokoll.CallEventRowUiMapperTest].
 */
class DashboardUiMapperTest {

    private lateinit var catalog: CountryCatalog
    private lateinit var mapper: DashboardUiMapper

    private val locale = Locale.US
    private val zone = ZoneOffset.UTC

    // today = 2026-07-13, windowStart = today.minusDays(29) = 2026-06-14 (30 inclusive days).
    private val now: Instant = Instant.parse("2026-07-13T12:00:00Z")
    private val today: LocalDate = LocalDate.parse("2026-07-13")
    private val windowStart: LocalDate = LocalDate.parse("2026-06-14")

    @Before
    fun setUp() {
        catalog = mockk()
        mapper = DashboardUiMapper(catalog)
    }

    private fun instantAt(date: LocalDate, hour: Int = 0): Long =
        date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `toSparkline returns exactly 30 buckets for an empty input`() {
        val sparkline = mapper.toSparkline(emptyList(), now, zone)

        assertEquals(30, sparkline.size)
    }

    @Test
    fun `toSparkline orders buckets chronologically oldest first`() {
        val sparkline = mapper.toSparkline(emptyList(), now, zone)

        assertEquals(windowStart, sparkline.first().date)
        assertEquals(today, sparkline.last().date)
    }

    @Test
    fun `toSparkline zero-fills a day with no events`() {
        val sparkline = mapper.toSparkline(emptyList(), now, zone)

        assertTrue(sparkline.all { it.count == 0 })
    }

    @Test
    fun `toSparkline counts multiple events landing on the same day`() {
        val millis = listOf(
            instantAt(windowStart, hour = 0),
            instantAt(windowStart, hour = 8),
            instantAt(windowStart, hour = 23),
        )

        val sparkline = mapper.toSparkline(millis, now, zone)

        assertEquals(3, sparkline.first { it.date == windowStart }.count)
    }

    @Test
    fun `toSparkline counts events on a different day independently`() {
        val millis = listOf(instantAt(today, hour = 0), instantAt(today, hour = 5))

        val sparkline = mapper.toSparkline(millis, now, zone)

        assertEquals(2, sparkline.first { it.date == today }.count)
    }

    @Test
    fun `toSparkline excludes a value strictly before the window start`() {
        val outOfWindow = instantAt(windowStart.minusDays(1), hour = 23)
        val inWindow = instantAt(windowStart, hour = 0)

        val sparkline = mapper.toSparkline(listOf(outOfWindow, inWindow), now, zone)

        assertEquals(1, sparkline.sumOf { it.count })
        assertEquals(1, sparkline.first { it.date == windowStart }.count)
    }

    @Test
    fun `toSparkline places an instant at exact local midnight into that same day's bucket`() {
        val midnightMillis = windowStart.plusDays(5).atStartOfDay(zone).toInstant().toEpochMilli()

        val sparkline = mapper.toSparkline(listOf(midnightMillis), now, zone)

        assertEquals(1, sparkline.first { it.date == windowStart.plusDays(5) }.count)
        assertEquals(1, sparkline.sumOf { it.count })
    }

    @Test
    fun `toTopCountry with a resolvable region code produces a Known result`() {
        val austria = Country("AT", 43)
        every { catalog.byIso2("AT") } returns Optional.of(austria)

        val result = mapper.toTopCountry(RegionCount("AT", 7), locale)

        assertEquals(TopCountryUi.Known(austria.flagEmoji(), austria.displayName(locale), 7), result)
    }

    @Test
    fun `toTopCountry with an unresolvable region code produces an Unknown result preserving the code and count`() {
        every { catalog.byIso2("XX") } returns Optional.empty()

        val result = mapper.toTopCountry(RegionCount("XX", 4), locale)

        assertEquals(TopCountryUi.Unknown("XX", 4), result)
    }

    private fun rule(id: String, patternText: String = "+436631234567"): Rule = Rule(
        id,
        PatternSyntax.parse(patternText),
        RuleAction.BLOCK,
        true,
        null,
        Instant.EPOCH,
    )

    @Test
    fun `toTopRules resolves a ruleId present in rulesById to a Known row matching the RuleRowUiMapper output`() {
        val ruleRowMapper = RuleRowUiMapper(catalog)
        val liveRule = rule("rule-live")
        val expectedRow = ruleRowMapper.toRow(liveRule, locale)

        val result = mapper.toTopRules(
            listOf(RuleIdCount("rule-live", 5)),
            mapOf("rule-live" to liveRule),
            ruleRowMapper,
            locale,
        )

        assertEquals(listOf(TopRuleUi.Known(expectedRow, 5)), result)
    }

    @Test
    fun `toTopRules resolves a ruleId absent from rulesById to Deleted carrying the count`() {
        val ruleRowMapper = RuleRowUiMapper(catalog)

        val result = mapper.toTopRules(
            listOf(RuleIdCount("rule-gone", 3)),
            emptyMap(),
            ruleRowMapper,
            locale,
        )

        assertEquals(listOf<TopRuleUi>(TopRuleUi.Deleted(3)), result)
    }

    @Test
    fun `toTopRules preserves the input order across Known and Deleted entries`() {
        val ruleRowMapper = RuleRowUiMapper(catalog)
        val liveRule = rule("rule-live")

        val result = mapper.toTopRules(
            listOf(RuleIdCount("rule-gone", 9), RuleIdCount("rule-live", 2)),
            mapOf("rule-live" to liveRule),
            ruleRowMapper,
            locale,
        )

        assertTrue(result[0] is TopRuleUi.Deleted)
        assertTrue(result[1] is TopRuleUi.Known)
        assertFalse(result[0] is TopRuleUi.Known)
    }

    @Test
    fun `toBreakdown returns all five categories in fixed order with zero counts for an empty input`() {
        val result = mapper.toBreakdown(emptyList())

        assertEquals(
            listOf(
                BreakdownEntry(BreakdownCategory.BLOCK, 0),
                BreakdownEntry(BreakdownCategory.SILENCE, 0),
                BreakdownEntry(BreakdownCategory.ALLOW_RULE, 0),
                BreakdownEntry(BreakdownCategory.CONTACT, 0),
                BreakdownEntry(BreakdownCategory.NO_RULE, 0),
            ),
            result,
        )
    }

    @Test
    fun `toBreakdown maps BLOCK action to the BLOCK category regardless of reason`() {
        val result = mapper.toBreakdown(listOf(ActionReasonCount(RuleAction.BLOCK.name, DecisionReason.RULE_MATCH.name, 3)))

        assertEquals(3, result.single { it.category == BreakdownCategory.BLOCK }.count)
    }

    @Test
    fun `toBreakdown maps SILENCE action to the SILENCE category`() {
        val result = mapper.toBreakdown(listOf(ActionReasonCount(RuleAction.SILENCE.name, DecisionReason.RULE_MATCH.name, 4)))

        assertEquals(4, result.single { it.category == BreakdownCategory.SILENCE }.count)
    }

    @Test
    fun `toBreakdown maps ALLOW with RULE_MATCH to ALLOW_RULE`() {
        val result = mapper.toBreakdown(listOf(ActionReasonCount(RuleAction.ALLOW.name, DecisionReason.RULE_MATCH.name, 5)))

        assertEquals(5, result.single { it.category == BreakdownCategory.ALLOW_RULE }.count)
    }

    @Test
    fun `toBreakdown maps CONTACT_BYPASS reason to CONTACT`() {
        val result = mapper.toBreakdown(listOf(ActionReasonCount(RuleAction.ALLOW.name, DecisionReason.CONTACT_BYPASS.name, 6)))

        assertEquals(6, result.single { it.category == BreakdownCategory.CONTACT }.count)
    }

    @Test
    fun `toBreakdown maps NO_MATCH reason to NO_RULE`() {
        val result = mapper.toBreakdown(listOf(ActionReasonCount(RuleAction.ALLOW.name, DecisionReason.NO_MATCH.name, 7)))

        assertEquals(7, result.single { it.category == BreakdownCategory.NO_RULE }.count)
    }

    @Test
    fun `toBreakdown sums multiple rows into the same category`() {
        val result = mapper.toBreakdown(
            listOf(
                ActionReasonCount(RuleAction.BLOCK.name, DecisionReason.RULE_MATCH.name, 2),
                ActionReasonCount(RuleAction.BLOCK.name, DecisionReason.RULE_MATCH.name, 3),
            ),
        )

        assertEquals(5, result.single { it.category == BreakdownCategory.BLOCK }.count)
    }

    @Test
    fun `toBreakdown drops a row whose action no longer parses`() {
        val result = mapper.toBreakdown(listOf(ActionReasonCount("GARBAGE", DecisionReason.RULE_MATCH.name, 9)))

        assertTrue(result.all { it.count == 0 })
    }

    @Test
    fun `toBreakdown drops a row whose reason no longer parses`() {
        val result = mapper.toBreakdown(listOf(ActionReasonCount(RuleAction.ALLOW.name, "GARBAGE", 9)))

        assertTrue(result.all { it.count == 0 })
    }
}
