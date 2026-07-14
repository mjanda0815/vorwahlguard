package io.janda.vorwahlguard.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class CallEventTest {

    @Test
    void isPlainImmutableData() {
        Instant now = Instant.parse("2026-07-12T10:00:00Z");
        CallEvent event =
                new CallEvent(
                        "event-1",
                        now,
                        "+436631234567",
                        "AT",
                        "rule-1",
                        RuleAction.BLOCK,
                        DecisionReason.RULE_MATCH);

        assertThat(event.id()).isEqualTo("event-1");
        assertThat(event.occurredAt()).isEqualTo(now);
        assertThat(event.numberOrHash()).isEqualTo("+436631234567");
        assertThat(event.regionCode()).isEqualTo("AT");
        assertThat(event.matchedRuleId()).isEqualTo("rule-1");
        assertThat(event.action()).isEqualTo(RuleAction.BLOCK);
        assertThat(event.reason()).isEqualTo(DecisionReason.RULE_MATCH);
    }

    @Test
    void reasonOnlyEventHasNullMatchedRuleId() {
        Instant now = Instant.parse("2026-07-12T10:00:00Z");
        CallEvent event =
                new CallEvent(
                        "event-2",
                        now,
                        "+436631234567",
                        "AT",
                        null,
                        RuleAction.ALLOW,
                        DecisionReason.NO_MATCH);

        assertThat(event.matchedRuleId()).isNull();
        assertThat(event.action()).isEqualTo(RuleAction.ALLOW);
        assertThat(event.reason()).isEqualTo(DecisionReason.NO_MATCH);
    }
}
