package io.janda.vorwahlguard.domain.model;

/**
 * The single validation entry point for the CLAUDE.md §4 pattern grammar:
 *
 * <pre>
 * +43*            prefix match, all Austrian numbers
 * +43663*         prefix match, one operator range
 * +436631234567   exact match
 * *               matches every number
 * PRIVATE         reserved token: withheld / unknown caller id
 * </pre>
 *
 * <p>Side-effect-free and UI-agnostic on purpose: a future Compose input screen
 * (PROJECT.md §7 "Vorwahl eingeben") must call {@link #isValid(String)} for live validation
 * instead of duplicating this grammar in Kotlin (CLAUDE.md §4).
 */
public final class PatternSyntax {

    private static final String PRIVATE_TOKEN = "PRIVATE";
    private static final String ANY_TOKEN = "*";
    private static final int MAX_E164_DIGITS = 15;

    private PatternSyntax() {
    }

    /** {@code true} if {@code text} is a syntactically valid pattern under the CLAUDE.md §4 grammar. */
    public static boolean isValid(String text) {
        return kindOf(text) != null;
    }

    /**
     * Parses {@code text} into a {@link Pattern}.
     *
     * @throws IllegalArgumentException if {@code text} does not satisfy {@link #isValid(String)}
     */
    public static Pattern parse(String text) {
        PatternKind kind = kindOf(text);
        if (kind == null) {
            throw new IllegalArgumentException("Invalid pattern syntax");
        }
        return new Pattern(kind, text);
    }

    private static PatternKind kindOf(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        if (text.equals(PRIVATE_TOKEN)) {
            return PatternKind.PRIVATE;
        }
        if (text.equals(ANY_TOKEN)) {
            return PatternKind.ANY;
        }
        if (text.charAt(0) != '+') {
            return null;
        }

        int starIndex = text.indexOf('*');
        String digitsPart;
        PatternKind kind;
        if (starIndex == -1) {
            digitsPart = text.substring(1);
            kind = PatternKind.EXACT;
        } else {
            // Exactly one trailing '*', only at the end (CLAUDE.md §4): reject if the '*'
            // is anywhere but the last character — this also rejects multiple wildcards,
            // since a second '*' can never be both present and not the last character
            // unless the first one already violated this check.
            if (starIndex != text.length() - 1) {
                return null;
            }
            digitsPart = text.substring(1, starIndex);
            kind = PatternKind.PREFIX;
        }

        if (digitsPart.isEmpty() || digitsPart.length() > MAX_E164_DIGITS) {
            return null;
        }
        for (int i = 0; i < digitsPart.length(); i++) {
            char c = digitsPart.charAt(i);
            // Deliberately ASCII '0'-'9' only, not Character.isDigit(): that accepts
            // Unicode digit scripts (e.g. Arabic-Indic) which are not valid E.164 digits.
            if (c < '0' || c > '9') {
                return null;
            }
        }
        // No real E.164 calling code starts with '0' (CLAUDE.md §4: a wildcard-free pattern
        // must be a syntactically valid E.164 number). The leading digit right after '+' is
        // a calling-code digit for both EXACT and PREFIX — a prefix like "+0*" is held to the
        // same rule, since it can never resolve to a real country either.
        if (digitsPart.charAt(0) == '0') {
            return null;
        }
        return kind;
    }
}
