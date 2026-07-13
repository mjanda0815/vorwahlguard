package io.janda.vorwahlguard.ui.regeln.addrule

import io.janda.vorwahlguard.domain.model.Pattern
import io.janda.vorwahlguard.domain.model.Rule
import io.janda.vorwahlguard.domain.model.RuleAction
import io.janda.vorwahlguard.domain.port.out.NumberNormalizer
import io.janda.vorwahlguard.domain.service.RuleMatcher
import java.time.Instant

/** Outcome of testing a raw number typed into "Nummer testen" against the draft + persisted rules. */
sealed interface TestOutcome {
    data class DraftMatched(val action: RuleAction) : TestOutcome
    data class ExistingRuleMatched(val rule: Rule, val action: RuleAction) : TestOutcome
    data object NoMatch : TestOutcome
    data object InvalidNumber : TestOutcome
}

/**
 * Pure helper (no coroutines, no DI) behind "Nummer testen": normalizes the raw input via
 * [NumberNormalizer] and runs it through the real domain [RuleMatcher] — never a hand-rolled
 * prefix comparison — over the persisted rules plus the in-progress draft rule.
 *
 * The draft is given a sentinel id ([DRAFT_RULE_ID]) that can never collide with a persisted
 * [Rule.id] (those are always [java.util.UUID] text from `RuleWriter`) so the winning rule can
 * be told apart from a real, already-saved rule after [RuleMatcher] picks it.
 */
class DraftRuleTester(
    private val normalizer: NumberNormalizer,
    private val matcher: RuleMatcher = RuleMatcher(),
) {

    fun test(
        persistedRules: List<Rule>,
        draftPattern: Pattern,
        draftAction: RuleAction,
        rawInput: String,
        defaultRegion: String?,
    ): TestOutcome {
        val number = normalizer.normalize(rawInput, defaultRegion)
        if (number.isUnknown()) {
            return TestOutcome.InvalidNumber
        }

        val draftRule = Rule(DRAFT_RULE_ID, draftPattern, draftAction, true, null, Instant.EPOCH)
        val decision = matcher.match(persistedRules + draftRule, number)

        if (!decision.matched()) {
            return TestOutcome.NoMatch
        }
        if (decision.matchedRuleId() == DRAFT_RULE_ID) {
            return TestOutcome.DraftMatched(decision.action())
        }
        val matchedRule = persistedRules.firstOrNull { it.id() == decision.matchedRuleId() }
            ?: return TestOutcome.NoMatch
        return TestOutcome.ExistingRuleMatched(matchedRule, decision.action())
    }

    private companion object {
        const val DRAFT_RULE_ID = "draft-rule-sentinel"
    }
}
