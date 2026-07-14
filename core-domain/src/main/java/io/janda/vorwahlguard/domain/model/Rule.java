package io.janda.vorwahlguard.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A user-created rule. A whitelist entry needs no special modelling — it is just a
 * {@link RuleAction#ALLOW} rule with an {@link PatternKind#EXACT} pattern; the standard
 * conflict resolution already makes it beat any shorter {@code BLOCK}/{@code SILENCE} prefix
 * (PROJECT.md §3, CLAUDE.md §4).
 *
 * <p>Plain class, not a {@code record} — see {@link Settings}'s class doc for why.
 */
public final class Rule {

    private final String id;
    private final Pattern pattern;
    private final RuleAction action;
    private final boolean enabled;
    private final String label;
    private final Instant createdAt;

    public Rule(
            String id,
            Pattern pattern,
            RuleAction action,
            boolean enabled,
            String label,
            Instant createdAt) {
        this.id = id;
        this.pattern = pattern;
        this.action = action;
        this.enabled = enabled;
        this.label = label;
        this.createdAt = createdAt;
    }

    public String id() {
        return id;
    }

    public Pattern pattern() {
        return pattern;
    }

    public RuleAction action() {
        return action;
    }

    public boolean enabled() {
        return enabled;
    }

    public String label() {
        return label;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Rule other)) return false;
        return enabled == other.enabled
                && Objects.equals(id, other.id)
                && Objects.equals(pattern, other.pattern)
                && action == other.action
                && Objects.equals(label, other.label)
                && Objects.equals(createdAt, other.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, pattern, action, enabled, label, createdAt);
    }

    @Override
    public String toString() {
        return "Rule[id=" + id
                + ", pattern=" + pattern
                + ", action=" + action
                + ", enabled=" + enabled
                + ", label=" + label
                + ", createdAt=" + createdAt
                + "]";
    }
}
