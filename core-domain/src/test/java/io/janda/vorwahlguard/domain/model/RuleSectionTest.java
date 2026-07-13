package io.janda.vorwahlguard.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class RuleSectionTest {

    @Test
    void exactAllowIsWhitelist() {
        assertThat(RuleSection.of(ruleFor("+436631234567", RuleAction.ALLOW))).isEqualTo(RuleSection.WHITELIST);
    }

    @Test
    void prefixAllowIsBlacklist() {
        assertThat(RuleSection.of(ruleFor("+43*", RuleAction.ALLOW))).isEqualTo(RuleSection.BLACKLIST);
    }

    @Test
    void anyAllowIsBlacklist() {
        assertThat(RuleSection.of(ruleFor("*", RuleAction.ALLOW))).isEqualTo(RuleSection.BLACKLIST);
    }

    @Test
    void privateAllowIsBlacklist() {
        assertThat(RuleSection.of(ruleFor("PRIVATE", RuleAction.ALLOW))).isEqualTo(RuleSection.BLACKLIST);
    }

    @Test
    void exactBlockIsBlacklist() {
        assertThat(RuleSection.of(ruleFor("+436631234567", RuleAction.BLOCK))).isEqualTo(RuleSection.BLACKLIST);
    }

    @Test
    void exactSilenceIsBlacklist() {
        assertThat(RuleSection.of(ruleFor("+436631234567", RuleAction.SILENCE))).isEqualTo(RuleSection.BLACKLIST);
    }

    private static Rule ruleFor(String patternText, RuleAction action) {
        return new Rule("id", PatternSyntax.parse(patternText), action, true, null, Instant.EPOCH);
    }
}
