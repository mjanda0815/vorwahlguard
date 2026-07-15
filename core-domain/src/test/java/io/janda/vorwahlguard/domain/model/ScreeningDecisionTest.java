package io.janda.vorwahlguard.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void ruleMatchWithoutARuleIdIsRejected() {
        assertThatThrownBy(() -> new ScreeningDecision(RuleAction.BLOCK, null, DecisionReason.RULE_MATCH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aNonRuleMatchReasonWithARuleIdIsRejected() {
        assertThatThrownBy(() -> new ScreeningDecision(RuleAction.ALLOW, "rule-1", DecisionReason.NO_MATCH))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScreeningDecision(RuleAction.ALLOW, "rule-1", DecisionReason.CONTACT_BYPASS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullActionOrReasonIsRejected() {
        assertThatThrownBy(() -> new ScreeningDecision(null, null, DecisionReason.NO_MATCH))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ScreeningDecision(RuleAction.ALLOW, null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
