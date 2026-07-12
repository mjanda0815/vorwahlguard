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
    }

    @Test
    void decisionWithRuleIdIsMatched() {
        ScreeningDecision decision = new ScreeningDecision(RuleAction.BLOCK, "rule-1");

        assertThat(decision.matched()).isTrue();
    }
}
