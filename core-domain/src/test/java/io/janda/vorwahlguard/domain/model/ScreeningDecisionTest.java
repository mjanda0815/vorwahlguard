package io.janda.vorwahlguard.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ScreeningDecisionTest {

    @Test
    void allowFactoryIsUnmatchedAllow() {
        ScreeningDecision decision = ScreeningDecision.allow();

        assertThat(decision.action()).isEqualTo(RuleAction.ALLOW);
        assertThat(decision.matchedRuleId()).isNull();
        assertThat(decision.matched()).isFalse();
        assertThat(decision.reason()).isEqualTo(DecisionReason.NO_MATCH);
    }

    @Test
    void decisionWithRuleIdIsMatched() {
        ScreeningDecision decision = new ScreeningDecision(RuleAction.BLOCK, "rule-1");

        assertThat(decision.matched()).isTrue();
    }

    @Test
    void twoArgCtorImpliesRuleMatch() {
        ScreeningDecision decision = new ScreeningDecision(RuleAction.BLOCK, "rule-1");

        assertThat(decision.reason()).isEqualTo(DecisionReason.RULE_MATCH);
    }

    @Test
    void contactBypassFactoryIsUnmatchedAllowWithContactBypassReason() {
        ScreeningDecision decision = ScreeningDecision.contactBypass();

        assertThat(decision.action()).isEqualTo(RuleAction.ALLOW);
        assertThat(decision.matchedRuleId()).isNull();
        assertThat(decision.matched()).isFalse();
        assertThat(decision.reason()).isEqualTo(DecisionReason.CONTACT_BYPASS);
    }

    @Test
    void allowAndContactBypassAreNotEqual() {
        assertThat(ScreeningDecision.allow()).isNotEqualTo(ScreeningDecision.contactBypass());
    }
}
