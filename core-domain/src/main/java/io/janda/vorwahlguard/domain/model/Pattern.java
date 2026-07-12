package io.janda.vorwahlguard.domain.model;

import java.util.Objects;

/**
 * A parsed, grammar-valid pattern (CLAUDE.md §4). Instances are only ever produced by
 * {@link PatternSyntax#parse(String)}, which is why the constructor is package-private —
 * every {@code Pattern} in the system is guaranteed to already be syntactically valid, so
 * {@link #matches(PhoneNumber)} and {@link #specificity()} never have to re-validate.
 */
public final class Pattern {

    private final PatternKind kind;
    private final String text;
    private final String digits;

    Pattern(PatternKind kind, String text) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.text = Objects.requireNonNull(text, "text");
        this.digits = switch (kind) {
            case EXACT -> text.substring(1);
            case PREFIX -> text.substring(1, text.length() - 1);
            case ANY, PRIVATE -> null;
        };
    }

    public PatternKind kind() {
        return kind;
    }

    /** The original, canonical pattern text, e.g. {@code "+43663*"}, {@code "PRIVATE"}. */
    public String text() {
        return text;
    }

    /**
     * The digit string with the leading {@code +} and, for {@link PatternKind#PREFIX}, the
     * trailing {@code *} stripped. {@code null} for {@link PatternKind#ANY} and
     * {@link PatternKind#PRIVATE}, which carry no digits.
     */
    public String digits() {
        return digits;
    }

    /**
     * CLAUDE.md §4: EXACT/PREFIX/ANY only ever match a known number; PRIVATE only ever
     * matches {@link PhoneNumber#UNKNOWN}. A withheld caller needs an explicit PRIVATE rule —
     * bare {@code *} does not catch it.
     */
    public boolean matches(PhoneNumber number) {
        Objects.requireNonNull(number, "number");
        return switch (kind) {
            case EXACT -> number.isKnown() && number.e164().equals("+" + digits);
            case PREFIX -> number.isKnown() && number.e164().startsWith("+" + digits);
            case ANY -> number.isKnown();
            case PRIVATE -> number.isUnknown();
        };
    }

    /**
     * Ordering key for the CLAUDE.md §4 conflict-resolution rule 1 ("longest matching prefix
     * wins, an exact match is the longest possible prefix"): higher is more specific.
     * EXACT and PREFIX rank by digit count (an exact match's digit count is the full number,
     * so it is never shorter than any prefix of itself). ANY and PRIVATE — which do not
     * compete on digit count — both rank lowest; they never compete with each other in
     * practice since their match domains ({@link PhoneNumber#isKnown()} vs.
     * {@link PhoneNumber#isUnknown()}) are disjoint.
     */
    public int specificity() {
        return switch (kind) {
            case EXACT, PREFIX -> digits.length() + 1;
            case ANY, PRIVATE -> 0;
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Pattern other)) {
            return false;
        }
        return kind == other.kind && text.equals(other.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, text);
    }

    @Override
    public String toString() {
        return "Pattern{kind=" + kind + ", text='" + text + "'}";
    }
}
