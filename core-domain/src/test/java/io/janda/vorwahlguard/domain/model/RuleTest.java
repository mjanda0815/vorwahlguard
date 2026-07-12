package io.janda.vorwahlguard.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class RuleTest {

    @Test
    void isPlainImmutableData() {
        Instant createdAt = Instant.parse("2026-07-12T10:00:00Z");
        Pattern pattern = PatternSyntax.parse("+43*");
        Rule rule = new Rule("rule-1", pattern, RuleAction.SILENCE, true, "Austria", createdAt);

        assertThat(rule.id()).isEqualTo("rule-1");
        assertThat(rule.pattern()).isEqualTo(pattern);
        assertThat(rule.action()).isEqualTo(RuleAction.SILENCE);
        assertThat(rule.enabled()).isTrue();
        assertThat(rule.label()).isEqualTo("Austria");
        assertThat(rule.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void whitelistEntryIsJustAnExactAllowRule() {
        Pattern exact = PatternSyntax.parse("+436631234567");
        Rule whitelistEntry = new Rule("rule-2", exact, RuleAction.ALLOW, true, "Grandma", Instant.now());

        assertThat(whitelistEntry.pattern().kind()).isEqualTo(PatternKind.EXACT);
        assertThat(whitelistEntry.action()).isEqualTo(RuleAction.ALLOW);
    }
}
