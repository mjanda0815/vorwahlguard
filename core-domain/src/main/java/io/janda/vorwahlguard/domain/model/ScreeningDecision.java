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

    public ScreeningDecision(RuleAction action, String matchedRuleId) {
        this.action = action;
        this.matchedRuleId = matchedRuleId;
    }

    public static ScreeningDecision allow() {
        return new ScreeningDecision(RuleAction.ALLOW, null);
    }

    public RuleAction action() {
        return action;
    }

    public String matchedRuleId() {
        return matchedRuleId;
    }

    /** {@code true} if a rule actually fired (as opposed to the "no match" default allow). */
    public boolean matched() {
        return matchedRuleId != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScreeningDecision other)) return false;
        return action == other.action && Objects.equals(matchedRuleId, other.matchedRuleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(action, matchedRuleId);
    }

    @Override
    public String toString() {
        return "ScreeningDecision[action=" + action + ", matchedRuleId=" + matchedRuleId + "]";
    }
}
