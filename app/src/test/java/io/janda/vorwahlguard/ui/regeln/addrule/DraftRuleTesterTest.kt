package io.janda.vorwahlguard.ui.regeln.addrule

import io.janda.vorwahlguard.domain.model.PatternSyntax
import io.janda.vorwahlguard.domain.model.PhoneNumber
import io.janda.vorwahlguard.domain.model.Rule
import io.janda.vorwahlguard.domain.model.RuleAction
import io.janda.vorwahlguard.domain.port.out.NumberNormalizer
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Covers "Nummer testen" (issue #6): [DraftRuleTester] must normalize via the injected
 * [NumberNormalizer] and then delegate the actual conflict resolution to the real, default
 * [io.janda.vorwahlguard.domain.service.RuleMatcher] rather than re-implementing CLAUDE.md §4
 * specificity/tie-break rules by hand.
 */
class DraftRuleTesterTest {

    private lateinit var normalizer: NumberNormalizer
    private lateinit var tester: DraftRuleTester

    @Before
    fun setUp() {
        normalizer = mockk()
        // Default-constructed RuleMatcher: proves the real production wiring, not a stub matcher.
        tester = DraftRuleTester(normalizer)
    }

    private fun rule(
        id: String,
        patternText: String,
        action: RuleAction,
        enabled: Boolean = true,
    ): Rule = Rule(id, PatternSyntax.parse(patternText), action, enabled, null, Instant.EPOCH)

    @Test
    fun `unparseable raw input yields InvalidNumber regardless of rules`() {
        every { normalizer.normalize("garbage", "AT") } returns PhoneNumber.UNKNOWN
        val persisted = listOf(rule("existing-1", "+43*", RuleAction.BLOCK))

        val outcome = tester.test(
            persistedRules = persisted,
            draftPattern = PatternSyntax.parse("*"),
            draftAction = RuleAction.BLOCK,
            rawInput = "garbage",
            defaultRegion = "AT",
        )

        assertEquals(TestOutcome.InvalidNumber, outcome)
    }

    @Test
    fun `draft pattern matches the number when there are no persisted rules`() {
        val number = PhoneNumber("+43663123456", "+43663123456", "AT")
        every { normalizer.normalize("+43663123456", "AT") } returns number

        val outcome = tester.test(
            persistedRules = emptyList(),
            draftPattern = PatternSyntax.parse("+43663*"),
            draftAction = RuleAction.SILENCE,
            rawInput = "+43663123456",
            defaultRegion = "AT",
        )

        assertEquals(TestOutcome.DraftMatched(RuleAction.SILENCE), outcome)
    }

    @Test
    fun `exact persisted rule beats a broader matching draft prefix`() {
        val number = PhoneNumber("+436631234567", "+436631234567", "AT")
        every { normalizer.normalize("+436631234567", "AT") } returns number
        val persisted = rule("existing-1", "+436631234567", RuleAction.ALLOW)

        val outcome = tester.test(
            persistedRules = listOf(persisted),
            draftPattern = PatternSyntax.parse("+43*"),
            draftAction = RuleAction.BLOCK,
            rawInput = "+436631234567",
            defaultRegion = "AT",
        )

        assertEquals(TestOutcome.ExistingRuleMatched(persisted, RuleAction.ALLOW), outcome)
    }

    @Test
    fun `a more specific draft wins over a broader persisted rule`() {
        val number = PhoneNumber("+436631234567", "+436631234567", "AT")
        every { normalizer.normalize("+436631234567", "AT") } returns number
        val persisted = rule("existing-1", "+43*", RuleAction.BLOCK)

        val outcome = tester.test(
            persistedRules = listOf(persisted),
            draftPattern = PatternSyntax.parse("+43663*"),
            draftAction = RuleAction.SILENCE,
            rawInput = "+436631234567",
            defaultRegion = "AT",
        )

        assertEquals(TestOutcome.DraftMatched(RuleAction.SILENCE), outcome)
    }

    @Test
    fun `no persisted rule and no draft pattern match yields NoMatch`() {
        val number = PhoneNumber("+4915112345678", "+4915112345678", "DE")
        every { normalizer.normalize("+4915112345678", "AT") } returns number
        val persisted = rule("existing-1", "+43*", RuleAction.BLOCK)

        val outcome = tester.test(
            persistedRules = listOf(persisted),
            draftPattern = PatternSyntax.parse("+41*"),
            draftAction = RuleAction.SILENCE,
            rawInput = "+4915112345678",
            defaultRegion = "AT",
        )

        assertEquals(TestOutcome.NoMatch, outcome)
    }

    @Test
    fun `equal specificity tie break lets ALLOW beat BLOCK per RuleMatcher precedence`() {
        val number = PhoneNumber("+43663123456", "+43663123456", "AT")
        every { normalizer.normalize("+43663123456", "AT") } returns number
        // Same pattern text/specificity on both sides: draft ALLOW must win over persisted BLOCK
        // purely through RuleMatcher's RuleAction.precedence() tie-break, not any logic in
        // DraftRuleTester itself.
        val persisted = rule("existing-1", "+43663*", RuleAction.BLOCK)

        val outcome = tester.test(
            persistedRules = listOf(persisted),
            draftPattern = PatternSyntax.parse("+43663*"),
            draftAction = RuleAction.ALLOW,
            rawInput = "+43663123456",
            defaultRegion = "AT",
        )

        assertEquals(TestOutcome.DraftMatched(RuleAction.ALLOW), outcome)
    }

    @Test
    fun `equal specificity tie break lets SILENCE beat BLOCK on the persisted side`() {
        val number = PhoneNumber("+43663123456", "+43663123456", "AT")
        every { normalizer.normalize("+43663123456", "AT") } returns number
        val persisted = rule("existing-1", "+43663*", RuleAction.SILENCE)

        val outcome = tester.test(
            persistedRules = listOf(persisted),
            draftPattern = PatternSyntax.parse("+43663*"),
            draftAction = RuleAction.BLOCK,
            rawInput = "+43663123456",
            defaultRegion = "AT",
        )

        assertEquals(TestOutcome.ExistingRuleMatched(persisted, RuleAction.SILENCE), outcome)
    }

    @Test
    fun `a disabled persisted rule never matches even when it is the more specific pattern`() {
        val number = PhoneNumber("+436631234567", "+436631234567", "AT")
        every { normalizer.normalize("+436631234567", "AT") } returns number
        val disabled = rule("existing-1", "+436631234567", RuleAction.ALLOW, enabled = false)

        val outcome = tester.test(
            persistedRules = listOf(disabled),
            draftPattern = PatternSyntax.parse("+43*"),
            draftAction = RuleAction.BLOCK,
            rawInput = "+436631234567",
            defaultRegion = "AT",
        )

        assertEquals(TestOutcome.DraftMatched(RuleAction.BLOCK), outcome)
    }
}
