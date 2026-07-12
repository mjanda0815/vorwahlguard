package io.janda.vorwahlguard.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RuleActionTest {

    @Test
    void allowOutranksSilenceOutranksBlock() {
        assertThat(RuleAction.ALLOW.precedence()).isGreaterThan(RuleAction.SILENCE.precedence());
        assertThat(RuleAction.SILENCE.precedence()).isGreaterThan(RuleAction.BLOCK.precedence());
    }
}
