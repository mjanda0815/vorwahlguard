package io.janda.vorwahlguard.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.janda.vorwahlguard.domain.model.PatternSyntax;
import io.janda.vorwahlguard.domain.model.DecisionReason;
import io.janda.vorwahlguard.domain.model.PhoneNumber;
import io.janda.vorwahlguard.domain.model.Rule;
import io.janda.vorwahlguard.domain.model.RuleAction;
import io.janda.vorwahlguard.domain.model.ScreeningDecision;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuleMatcherTest {

    private final RuleMatcher matcher = new RuleMatcher();
    private static final Instant CREATED_AT = Instant.parse("2026-07-12T10:00:00Z");

    private static Rule rule(String id, String pattern, RuleAction action) {
        return new Rule(id, PatternSyntax.parse(pattern), action, true, id, CREATED_AT);
    }

    private static Rule disabledRule(String id, String pattern, RuleAction action) {
        return new Rule(id, PatternSyntax.parse(pattern), action, false, id, CREATED_AT);
    }

    @Test
    void longestPrefixWins() {
        PhoneNumber number = new PhoneNumber("raw", "+436631234567", "AT");
        List<Rule> rules = List.of(
                rule("country", "+43*", RuleAction.SILENCE),
                rule("operator", "+43663*", RuleAction.BLOCK));

        ScreeningDecision decision = matcher.match(rules, number);

        assertThat(decision.matchedRuleId()).isEqualTo("operator");
        assertThat(decision.action()).isEqualTo(RuleAction.BLOCK);
        assertThat(decision.reason()).isEqualTo(DecisionReason.RULE_MATCH);
    }

    @Test
    void exactAllowBeatsShorterBlockPrefix() {
        // PROJECT.md §3: whitelist entry — an EXACT ALLOW rule beats a broader BLOCK prefix.
        PhoneNumber grandma = new PhoneNumber("raw", "+436631234567", "AT");
        List<Rule> rules = List.of(
                rule("block-at", "+43*", RuleAction.BLOCK),
                rule("whitelist-grandma", "+436631234567", RuleAction.ALLOW));

        ScreeningDecision decision = matcher.match(rules, grandma);

        assertThat(decision.matchedRuleId()).isEqualTo("whitelist-grandma");
        assertThat(decision.action()).isEqualTo(RuleAction.ALLOW);
    }

    @Test
    void tieBreakAtEqualSpecificityPrefersAllowOverSilenceOverBlock() {
        PhoneNumber number = new PhoneNumber("raw", "+436631234567", "AT");
        List<Rule> blockThenAllow = List.of(
                rule("block", "+43*", RuleAction.BLOCK),
                rule("allow", "+43*", RuleAction.ALLOW),
                rule("silence", "+43*", RuleAction.SILENCE));

        ScreeningDecision decision = matcher.match(blockThenAllow, number);

        assertThat(decision.action()).isEqualTo(RuleAction.ALLOW);
        assertThat(decision.matchedRuleId()).isEqualTo("allow");
    }

    @Test
    void silenceBeatsBlockAtEqualSpecificityWhenNoAllowPresent() {
        PhoneNumber number = new PhoneNumber("raw", "+436631234567", "AT");
        List<Rule> blockThenSilence = List.of(
                rule("block", "+43*", RuleAction.BLOCK),
                rule("silence", "+43*", RuleAction.SILENCE));

        ScreeningDecision decision = matcher.match(blockThenSilence, number);

        assertThat(decision.action()).isEqualTo(RuleAction.SILENCE);
    }

    @Test
    void noMatchAllowsWithNullMatchedRuleId() {
        PhoneNumber number = new PhoneNumber("raw", "+491701234567", "DE");
        List<Rule> rules = List.of(rule("austria", "+43*", RuleAction.BLOCK));

        ScreeningDecision decision = matcher.match(rules, number);

        assertThat(decision.action()).isEqualTo(RuleAction.ALLOW);
        assertThat(decision.matchedRuleId()).isNull();
        assertThat(decision.matched()).isFalse();
        assertThat(decision.reason()).isEqualTo(DecisionReason.NO_MATCH);
    }

    @Test
    void emptyRuleSetAllows() {
        PhoneNumber number = new PhoneNumber("raw", "+436631234567", "AT");

        ScreeningDecision decision = matcher.match(List.of(), number);

        assertThat(decision).isEqualTo(ScreeningDecision.allow());
    }

    @Test
    void disabledRulesNeverMatch() {
        PhoneNumber number = new PhoneNumber("raw", "+436631234567", "AT");
        List<Rule> rules = List.of(disabledRule("disabled-block", "+43*", RuleAction.BLOCK));

        ScreeningDecision decision = matcher.match(rules, number);

        assertThat(decision).isEqualTo(ScreeningDecision.allow());
    }

    @Test
    void privateRuleMatchesUnknownNumber() {
        List<Rule> rules = List.of(rule("private", "PRIVATE", RuleAction.BLOCK));

        ScreeningDecision decision = matcher.match(rules, PhoneNumber.UNKNOWN);

        assertThat(decision.action()).isEqualTo(RuleAction.BLOCK);
        assertThat(decision.matchedRuleId()).isEqualTo("private");
    }

    @Test
    void anyRuleDoesNotMatchUnknownNumber() {
        List<Rule> rules = List.of(rule("catch-all", "*", RuleAction.BLOCK));

        ScreeningDecision decision = matcher.match(rules, PhoneNumber.UNKNOWN);

        assertThat(decision).isEqualTo(ScreeningDecision.allow());
    }

    @Test
    void exactRuleWinsOverSameDigitLengthPrefixRegardlessOfAction() {
        // "+436631234567*" is a degenerate PREFIX whose digit count equals the EXACT
        // pattern's digit count for the same number. Give the PREFIX rule the
        // higher-precedence action (ALLOW) and the EXACT rule the lower-precedence one
        // (BLOCK): if specificity ever tied between them, the RuleAction tie-break would
        // incorrectly hand this to the ALLOW prefix rule. The EXACT match must win outright
        // on specificity alone, before any tie-break is even considered (CLAUDE.md §4 rule 1).
        PhoneNumber number = new PhoneNumber("raw", "+436631234567", "AT");
        List<Rule> rules = List.of(
                rule("same-length-prefix", "+436631234567*", RuleAction.ALLOW),
                rule("exact", "+436631234567", RuleAction.BLOCK));

        ScreeningDecision decision = matcher.match(rules, number);

        assertThat(decision.matchedRuleId()).isEqualTo("exact");
        assertThat(decision.action()).isEqualTo(RuleAction.BLOCK);
    }
}
