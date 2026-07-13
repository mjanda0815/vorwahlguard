package io.janda.vorwahlguard.domain.model;

/**
 * Display taxonomy for the rule list (PROJECT.md §7): {@link Rule}s are not stored separately as
 * "whitelist" or "blacklist" entries — there is exactly one {@code rules} table — this is purely
 * a grouping computed from the same underlying {@link Rule} the screening hot path already uses.
 * As {@link Rule}'s own doc-comment notes, a whitelist entry is nothing more than an
 * {@link RuleAction#ALLOW} rule with an {@link PatternKind#EXACT} pattern; everything else is a
 * blacklist entry (a country/prefix-wide allow rule is still shown as "blacklist" here, since it
 * has collateral effects on other numbers and is not a one-to-one personal allow).
 */
public enum RuleSection {
    WHITELIST,
    BLACKLIST;

    /** {@link #WHITELIST} iff {@code rule} is an exact-match {@link RuleAction#ALLOW} rule. */
    public static RuleSection of(Rule rule) {
        boolean isWhitelist = rule.action() == RuleAction.ALLOW && rule.pattern().kind() == PatternKind.EXACT;
        return isWhitelist ? WHITELIST : BLACKLIST;
    }
}
