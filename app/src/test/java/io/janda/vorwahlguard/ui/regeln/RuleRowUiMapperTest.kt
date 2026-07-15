package io.janda.vorwahlguard.ui.regeln

import io.janda.vorwahlguard.domain.model.Country
import io.janda.vorwahlguard.domain.model.PatternDescription
import io.janda.vorwahlguard.domain.model.PatternSyntax
import io.janda.vorwahlguard.domain.model.Rule
import io.janda.vorwahlguard.domain.model.RuleAction
import io.janda.vorwahlguard.domain.port.out.CountryCatalog
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Covers [RuleRowUiMapper]: which [RuleLabel] a rule's [io.janda.vorwahlguard.domain.model.PatternKind]
 * resolves to, including the defensive fallback to [RuleLabel.Raw] when [CountryCatalog.describe]
 * returns an empty-but-unambiguous country list (an unrecognized calling code), and that
 * [RuleRowUi.patternText] always mirrors [io.janda.vorwahlguard.domain.model.Pattern.text]
 * regardless of the label branch chosen.
 */
class RuleRowUiMapperTest {

    private lateinit var catalog: CountryCatalog
    private lateinit var mapper: RuleRowUiMapper

    private val locale = Locale.GERMANY

    @Before
    fun setUp() {
        catalog = mockk()
        mapper = RuleRowUiMapper(catalog)
    }

    private fun rule(patternText: String, action: RuleAction = RuleAction.BLOCK): Rule {
        return Rule(
            "rule-1",
            PatternSyntax.parse(patternText),
            action,
            true,
            null,
            Instant.EPOCH,
        )
    }

    @Test
    fun `prefix pattern resolving to a single unambiguous country produces a Country label`() {
        val austria = Country("AT", 43)
        val rule = rule("+43*")
        every { catalog.describe(rule.pattern()) } returns PatternDescription(rule.pattern(), listOf(austria), false)

        val row = mapper.toRow(rule, locale)

        assertEquals(RuleLabel.Country(austria.flagEmoji(), austria.displayName(locale)), row.label)
    }

    @Test
    fun `prefix pattern resolving to an ambiguous calling code produces an AmbiguousCode label with every region`() {
        val rule = rule("+1*")
        val us = Country("US", 1)
        val ca = Country("CA", 1)
        every { catalog.describe(rule.pattern()) } returns PatternDescription(rule.pattern(), listOf(us, ca), true)

        val row = mapper.toRow(rule, locale)

        // The whole region list is kept (flag + localized name each, in the catalog's order),
        // not just the count (issue #91).
        assertEquals(
            RuleLabel.AmbiguousCode(
                listOf(
                    RegionEntry(us.flagEmoji(), us.displayName(locale)),
                    RegionEntry(ca.flagEmoji(), ca.displayName(locale)),
                ),
            ),
            row.label,
        )
    }

    @Test
    fun `prefix pattern resolving to an empty but unambiguous country list falls back to Raw`() {
        val rule = rule("+999*")
        every { catalog.describe(rule.pattern()) } returns PatternDescription(rule.pattern(), emptyList(), false)

        val row = mapper.toRow(rule, locale)

        assertEquals(RuleLabel.Raw, row.label)
    }

    @Test
    fun `PRIVATE pattern produces a Private label`() {
        val rule = rule("PRIVATE")

        val row = mapper.toRow(rule, locale)

        assertEquals(RuleLabel.Private, row.label)
    }

    @Test
    fun `bare wildcard pattern produces a Raw label`() {
        val rule = rule("*")

        val row = mapper.toRow(rule, locale)

        assertEquals(RuleLabel.Raw, row.label)
    }

    @Test
    fun `exact pattern produces a Raw label regardless of what describe would return`() {
        val rule = rule("+436631234567", RuleAction.ALLOW)

        val row = mapper.toRow(rule, locale)

        assertEquals(RuleLabel.Raw, row.label)
    }

    @Test
    fun `exact pattern never calls describe on the catalog`() {
        val rule = rule("+436631234567", RuleAction.ALLOW)

        mapper.toRow(rule, locale)

        verify(exactly = 0) { catalog.describe(any()) }
    }

    @Test
    fun `patternText mirrors the rule's pattern text for a Country label`() {
        val austria = Country("AT", 43)
        val rule = rule("+43*")
        every { catalog.describe(rule.pattern()) } returns PatternDescription(rule.pattern(), listOf(austria), false)

        val row = mapper.toRow(rule, locale)

        assertEquals("+43*", row.patternText)
    }

    @Test
    fun `patternText mirrors the rule's pattern text for a Raw label`() {
        val rule = rule("+436631234567", RuleAction.ALLOW)

        val row = mapper.toRow(rule, locale)

        assertEquals("+436631234567", row.patternText)
    }
}
