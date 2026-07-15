package io.janda.vorwahlguard.ui.protokoll

import io.janda.vorwahlguard.data.events.CallEventEntity
import io.janda.vorwahlguard.domain.model.Country
import io.janda.vorwahlguard.domain.model.DecisionReason
import io.janda.vorwahlguard.domain.model.RuleAction
import io.janda.vorwahlguard.domain.port.out.CountryCatalog
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.Optional
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Covers [CallEventRowUiMapper]: the ADR 0012 hashed-row-shows-region-not-number branch (and
 * that it genuinely never reads [CallEventEntity.numberOrHash]/[CallEventEntity.matchedRuleId]
 * for that branch), the unhashed-number branch, the `PRIVATE` branch (pinning the
 * `startsWith("+")` discriminator rather than a literal `"PRIVATE"` comparison), the lenient
 * `null`-on-unparseable-action fallback [io.janda.vorwahlguard.data.rules.RuleEntity] already
 * uses, and the exact [DateTimeFormatter] construction used for [CallEventRowUi.timestampText].
 */
class CallEventRowUiMapperTest {

    private lateinit var catalog: CountryCatalog
    private lateinit var mapper: CallEventRowUiMapper

    private val locale = Locale.US
    private val zone = ZoneId.of("UTC")
    private val fixedInstant: Instant = Instant.parse("2026-07-13T10:15:30Z")

    @Before
    fun setUp() {
        catalog = mockk()
        mapper = CallEventRowUiMapper(catalog)
    }

    private fun entity(
        id: String = "event-1",
        occurredAt: Instant = fixedInstant,
        numberOrHash: String = "+4915112345678",
        isHashed: Boolean = false,
        regionCode: String = "AT",
        matchedRuleId: String? = "rule-1",
        action: String = RuleAction.BLOCK.name,
        reason: String = DecisionReason.RULE_MATCH.name,
    ): CallEventEntity {
        return CallEventEntity(id, occurredAt, numberOrHash, isHashed, regionCode, matchedRuleId, action, reason)
    }

    @Test
    fun `hashed row with a resolvable region produces a Region display`() {
        val austria = Country("AT", 43)
        every { catalog.byIso2("AT") } returns Optional.of(austria)
        // Deliberately wrong-looking numberOrHash: if the mapper mistakenly read it instead of
        // trusting isHashed, this would produce Number("PRIVATE-should-not-be-read") instead.
        val row = mapper.toRow(
            entity(isHashed = true, regionCode = "AT", numberOrHash = "PRIVATE-should-not-be-read"),
            locale,
            zone,
        )

        assertEquals(CallEventDisplay.Region(austria.flagEmoji(), austria.displayName(locale)), row?.display)
    }

    @Test
    fun `hashed row never reads numberOrHash to build its display`() {
        val austria = Country("AT", 43)
        every { catalog.byIso2("AT") } returns Optional.of(austria)

        val row = mapper.toRow(
            entity(isHashed = true, regionCode = "AT", numberOrHash = "PRIVATE-should-not-be-read"),
            locale,
            zone,
        )

        // Proof by contradiction: a Number or Private display would mean numberOrHash was read.
        assertEquals(CallEventDisplay.Region::class.java, row?.display?.javaClass)
    }

    @Test
    fun `hashed row resolution asks the catalog for the entity's region code`() {
        val austria = Country("AT", 43)
        every { catalog.byIso2("AT") } returns Optional.of(austria)

        mapper.toRow(entity(isHashed = true, regionCode = "AT"), locale, zone)

        verify(exactly = 1) { catalog.byIso2("AT") }
    }

    @Test
    fun `hashed row with an unresolvable region code produces an UnknownRegion display`() {
        every { catalog.byIso2("UNKNOWN") } returns Optional.empty()

        val row = mapper.toRow(entity(isHashed = true, regionCode = "UNKNOWN"), locale, zone)

        assertEquals(CallEventDisplay.UnknownRegion("UNKNOWN"), row?.display)
    }

    @Test
    fun `unhashed row with an E164 number produces a Number display`() {
        val row = mapper.toRow(
            entity(isHashed = false, numberOrHash = "+4915112345678"),
            locale,
            zone,
        )

        assertEquals(CallEventDisplay.Number("+4915112345678"), row?.display)
    }

    @Test
    fun `unhashed row with the PRIVATE token produces a Private display`() {
        val row = mapper.toRow(entity(isHashed = false, numberOrHash = "PRIVATE"), locale, zone)

        assertEquals(CallEventDisplay.Private, row?.display)
    }

    @Test
    fun `Private is decided by the leading plus sign not by literal equality with PRIVATE`() {
        // Any non-E164 string without a leading '+' must resolve to Private, not just the exact
        // literal "PRIVATE" — this pins startsWith("+") as the discriminator.
        val row = mapper.toRow(entity(isHashed = false, numberOrHash = "withheld"), locale, zone)

        assertEquals(CallEventDisplay.Private, row?.display)
    }

    @Test
    fun `a row with an unparseable action returns null`() {
        val row = mapper.toRow(entity(action = "GARBAGE"), locale, zone)

        assertNull(row)
    }

    @Test
    fun `an unhashed number row exposes its E164 as the rule pattern`() {
        val row = mapper.toRow(entity(isHashed = false, numberOrHash = "+4915112345678"), locale, zone)

        assertEquals("+4915112345678", row?.rulePattern)
    }

    @Test
    fun `a withheld row exposes the PRIVATE token as the rule pattern`() {
        val row = mapper.toRow(entity(isHashed = false, numberOrHash = "PRIVATE"), locale, zone)

        assertEquals("PRIVATE", row?.rulePattern)
    }

    @Test
    fun `a hashed row exposes no rule pattern - a hash yields no number to build a rule from`() {
        every { catalog.byIso2("AT") } returns Optional.empty()

        val row = mapper.toRow(entity(isHashed = true, regionCode = "AT"), locale, zone)

        assertNull(row?.rulePattern)
    }

    @Test
    fun `a malformed stored number degrades to no rule pattern instead of a bad rule`() {
        // A stored "number" that is not a valid pattern (leading zero after '+') must leave the
        // row non-actionable, never fabricate an invalid EXACT rule.
        val row = mapper.toRow(entity(isHashed = false, numberOrHash = "+0123"), locale, zone)

        assertNull(row?.rulePattern)
    }

    @Test
    fun `timestampText matches the SHORT localized date time formatter for the given locale and zone`() {
        val expected = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(locale)
            .withZone(zone)
            .format(fixedInstant)

        val row = mapper.toRow(entity(occurredAt = fixedInstant), locale, zone)

        assertEquals(expected, row?.timestampText)
    }

    @Test
    fun `the resulting row id mirrors the entity id`() {
        val row = mapper.toRow(entity(id = "event-42"), locale, zone)

        assertEquals("event-42", row?.id)
    }

    @Test
    fun `the resulting row id mirrors a different entity id`() {
        val row = mapper.toRow(entity(id = "event-99"), locale, zone)

        assertEquals("event-99", row?.id)
    }

    @Test
    fun `the resulting row action mirrors the entity's parsed action`() {
        val row = mapper.toRow(entity(action = RuleAction.SILENCE.name), locale, zone)

        assertEquals(RuleAction.SILENCE, row?.action)
    }

    @Test
    fun `the resulting row action mirrors a different parsed action`() {
        val row = mapper.toRow(entity(action = RuleAction.ALLOW.name), locale, zone)

        assertEquals(RuleAction.ALLOW, row?.action)
    }

    @Test
    fun `a RULE_MATCH reason produces a null allowReason`() {
        val row = mapper.toRow(entity(reason = DecisionReason.RULE_MATCH.name), locale, zone)

        assertNull(row?.allowReason)
    }

    @Test
    fun `a CONTACT_BYPASS reason maps to AllowReasonUi CONTACT_BYPASS`() {
        val row = mapper.toRow(
            entity(matchedRuleId = null, action = RuleAction.ALLOW.name, reason = DecisionReason.CONTACT_BYPASS.name),
            locale,
            zone,
        )

        assertEquals(AllowReasonUi.CONTACT_BYPASS, row?.allowReason)
    }

    @Test
    fun `a NO_MATCH reason maps to AllowReasonUi NO_MATCH`() {
        val row = mapper.toRow(
            entity(matchedRuleId = null, action = RuleAction.ALLOW.name, reason = DecisionReason.NO_MATCH.name),
            locale,
            zone,
        )

        assertEquals(AllowReasonUi.NO_MATCH, row?.allowReason)
    }

    @Test
    fun `an unparseable reason leniently maps to a null allowReason instead of dropping the row`() {
        val row = mapper.toRow(entity(reason = "GARBAGE"), locale, zone)

        assertNull(row?.allowReason)
        // The row itself must still be produced — only the action needs to parse (CLAUDE.md
        // §12 statistics survive unrelated schema drift on a best-effort field).
        assertEquals(RuleAction.BLOCK, row?.action)
    }
}
