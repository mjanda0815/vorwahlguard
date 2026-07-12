package io.janda.vorwahlguard.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.janda.vorwahlguard.domain.model.PatternSyntax;
import io.janda.vorwahlguard.domain.model.PhoneNumber;
import io.janda.vorwahlguard.domain.model.Rule;
import io.janda.vorwahlguard.domain.model.RuleAction;
import io.janda.vorwahlguard.domain.model.ScreeningDecision;
import io.janda.vorwahlguard.domain.port.out.RuleRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScreenIncomingCallServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-07-12T10:00:00Z");
    private static final Instant AT = Instant.parse("2026-07-12T12:00:00Z");

    /** Fails the test loudly if the rule set is consulted when it should not be (ADR 0006). */
    private static final class ExplodingRuleRepository implements RuleRepository {
        @Override
        public List<Rule> activeRules() {
            throw new AssertionError("RuleRepository must not be queried when isKnownContact is true");
        }
    }

    private static final class StaticRuleRepository implements RuleRepository {
        private final List<Rule> rules;

        StaticRuleRepository(List<Rule> rules) {
            this.rules = rules;
        }

        @Override
        public List<Rule> activeRules() {
            return rules;
        }
    }

    @Test
    void knownContactIsAllowedWithoutConsultingRuleSet() {
        ScreenIncomingCallService service = new ScreenIncomingCallService(new ExplodingRuleRepository());
        PhoneNumber grandma = new PhoneNumber("raw", "+436631234567", "AT");

        ScreeningDecision decision = service.decide(grandma, true, AT);

        assertThat(decision).isEqualTo(ScreeningDecision.allow());
    }

    @Test
    void unknownContactWithMatchingBlockRuleIsBlocked() {
        Rule blockAustria = new Rule(
                "block-at", PatternSyntax.parse("+43*"), RuleAction.BLOCK, true, "Austria", CREATED_AT);
        ScreenIncomingCallService service =
                new ScreenIncomingCallService(new StaticRuleRepository(List.of(blockAustria)));
        PhoneNumber caller = new PhoneNumber("raw", "+436631234567", "AT");

        ScreeningDecision decision = service.decide(caller, false, AT);

        assertThat(decision.action()).isEqualTo(RuleAction.BLOCK);
        assertThat(decision.matchedRuleId()).isEqualTo("block-at");
    }

    @Test
    void unknownNumberWithPrivateBlockRuleIsBlocked() {
        Rule blockPrivate = new Rule(
                "block-private", PatternSyntax.parse("PRIVATE"), RuleAction.BLOCK, true, "Withheld", CREATED_AT);
        ScreenIncomingCallService service =
                new ScreenIncomingCallService(new StaticRuleRepository(List.of(blockPrivate)));

        ScreeningDecision decision = service.decide(PhoneNumber.UNKNOWN, false, AT);

        assertThat(decision.action()).isEqualTo(RuleAction.BLOCK);
        assertThat(decision.matchedRuleId()).isEqualTo("block-private");
    }
}
