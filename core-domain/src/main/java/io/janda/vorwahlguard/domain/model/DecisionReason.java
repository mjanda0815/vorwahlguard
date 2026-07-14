package io.janda.vorwahlguard.domain.model;

/**
 * Why a {@link ScreeningDecision} came out the way it did.
 *
 * <p>Invariant: {@code matchedRuleId != null} if and only if {@code reason == RULE_MATCH}. A
 * contact bypass or a no-match allow never carries a rule id (CLAUDE.md §4, §5).
 */
public enum DecisionReason {
    RULE_MATCH,
    CONTACT_BYPASS,
    NO_MATCH
}
