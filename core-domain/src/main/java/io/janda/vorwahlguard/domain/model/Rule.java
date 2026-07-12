package io.janda.vorwahlguard.domain.model;

import java.time.Instant;

/**
 * A user-created rule. A whitelist entry needs no special modelling — it is just a
 * {@link RuleAction#ALLOW} rule with an {@link PatternKind#EXACT} pattern; the standard
 * conflict resolution already makes it beat any shorter {@code BLOCK}/{@code SILENCE} prefix
 * (PROJECT.md §3, CLAUDE.md §4).
 */
public record Rule(
        String id,
        Pattern pattern,
        RuleAction action,
        boolean enabled,
        String label,
        Instant createdAt) {
}
