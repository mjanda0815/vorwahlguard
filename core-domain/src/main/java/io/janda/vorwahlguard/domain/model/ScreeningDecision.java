package io.janda.vorwahlguard.domain.model;

/**
 * The outcome of screening one call: the {@link RuleAction} to take and, if a {@link Rule}
 * fired, its id. CLAUDE.md §4 rule 3: no match means {@code ALLOW} with a {@code null}
 * {@code matchedRuleId} — {@link #allow()} builds exactly that.
 */
public record ScreeningDecision(RuleAction action, String matchedRuleId) {

    public static ScreeningDecision allow() {
        return new ScreeningDecision(RuleAction.ALLOW, null);
    }

    /** {@code true} if a rule actually fired (as opposed to the "no match" default allow). */
    public boolean matched() {
        return matchedRuleId != null;
    }
}
