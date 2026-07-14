package io.janda.vorwahlguard.domain.model;

import java.util.Objects;

/**
 * The outcome of screening one call: the {@link RuleAction} to take and, if a {@link Rule}
 * fired, its id. CLAUDE.md §4 rule 3: no match means {@code ALLOW} with a {@code null}
 * {@code matchedRuleId} — {@link #allow()} builds exactly that.
 *
 * <p>Plain class, not a {@code record} — see {@link Settings}'s class doc for why.
 */
public final class ScreeningDecision {

    private final RuleAction action;
    private final String matchedRuleId;
    private final DecisionReason reason;

    public ScreeningDecision(RuleAction action, String matchedRuleId, DecisionReason reason) {
        this.action = action;
        this.matchedRuleId = matchedRuleId;
        this.reason = reason;
    }

    /** Convenience ctor for rule matches — always carries {@link DecisionReason#RULE_MATCH}. */
    public ScreeningDecision(RuleAction action, String matchedRuleId) {
        this(action, matchedRuleId, DecisionReason.RULE_MATCH);
    }

    public static ScreeningDecision allow() {
        return new ScreeningDecision(RuleAction.ALLOW, null, DecisionReason.NO_MATCH);
    }

    /** A known contact was allowed unconditionally, before any rule was consulted. */
    public static ScreeningDecision contactBypass() {
        return new ScreeningDecision(RuleAction.ALLOW, null, DecisionReason.CONTACT_BYPASS);
    }

    public RuleAction action() {
        return action;
    }

    public String matchedRuleId() {
        return matchedRuleId;
    }

    public DecisionReason reason() {
        return reason;
    }

    /** {@code true} if a rule actually fired (as opposed to the "no match" default allow). */
    public boolean matched() {
        return matchedRuleId != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScreeningDecision other)) return false;
        return action == other.action
                && Objects.equals(matchedRuleId, other.matchedRuleId)
                && reason == other.reason;
    }

    @Override
    public int hashCode() {
        return Objects.hash(action, matchedRuleId, reason);
    }

    @Override
    public String toString() {
        return "ScreeningDecision[action=" + action
                + ", matchedRuleId=" + matchedRuleId
                + ", reason=" + reason
                + "]";
    }
}
