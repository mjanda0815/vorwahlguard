package io.janda.vorwahlguard.domain.model;

/**
 * The three screening actions (CLAUDE.md §5). {@link #precedence()} is the explicit tie-break
 * rank for the CLAUDE.md §4 conflict resolution rule 2: {@code ALLOW > SILENCE > BLOCK} at
 * equal pattern specificity. It is a deliberate field, not an assumption baked into enum
 * declaration order — reordering the constants below must not silently change the tie-break.
 */
public enum RuleAction {
    BLOCK(0),
    SILENCE(1),
    ALLOW(2);

    private final int precedence;

    RuleAction(int precedence) {
        this.precedence = precedence;
    }

    /** Higher wins a tie-break at equal pattern specificity. */
    public int precedence() {
        return precedence;
    }
}
