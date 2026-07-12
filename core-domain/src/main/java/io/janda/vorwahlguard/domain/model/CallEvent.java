package io.janda.vorwahlguard.domain.model;

import java.time.Instant;

/**
 * The shape of one recorded screening result (PROJECT.md §4). Plain data — no behaviour.
 * {@code numberOrHash} is either the E.164 number or {@code sha256(number + local salt)},
 * depending on {@link Settings#pseudonymiseNumbers()}; that choice is made by the {@code :app}
 * adapter, not here (CLAUDE.md §12).
 */
public record CallEvent(
        String id,
        Instant occurredAt,
        String numberOrHash,
        String regionCode,
        String matchedRuleId,
        RuleAction action) {
}
