package io.janda.vorwahlguard.domain.service;

import io.janda.vorwahlguard.domain.model.PhoneNumber;
import io.janda.vorwahlguard.domain.model.ScreeningDecision;
import io.janda.vorwahlguard.domain.port.in.ScreenIncomingCall;
import io.janda.vorwahlguard.domain.port.out.RuleRepository;
import java.time.Instant;
import java.util.Objects;

/**
 * The driving-port implementation. Plain Java, constructor injection, no framework
 * annotations — Hilt binds this in {@code :app} via an {@code @Provides} method
 * (docs/ARCHITECTURE.md); the domain must never see Dagger.
 *
 * <p>Per {@code docs/adr/0006-contacts-bypass-unconditional-priority.md}, a known contact is
 * allowed unconditionally, before the rule set is consulted at all — {@link RuleRepository}
 * is not queried in that branch.
 *
 * <p>{@code at} is intentionally unused in v1: recording and timestamps happen in {@code :app}
 * (M2/M3), not here. It stays in the {@link ScreenIncomingCall} signature because that
 * signature is fixed by ADR 0006.
 */
public final class ScreenIncomingCallService implements ScreenIncomingCall {

    private final RuleRepository ruleRepository;
    private final RuleMatcher ruleMatcher;

    public ScreenIncomingCallService(RuleRepository ruleRepository) {
        this.ruleRepository = Objects.requireNonNull(ruleRepository, "ruleRepository");
        this.ruleMatcher = new RuleMatcher();
    }

    @Override
    public ScreeningDecision decide(PhoneNumber number, boolean isKnownContact, Instant at) {
        if (isKnownContact) {
            return ScreeningDecision.contactBypass();
        }
        return ruleMatcher.match(ruleRepository.activeRules(), number);
    }
}
