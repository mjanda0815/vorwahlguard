package io.janda.vorwahlguard.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Read model for the rule list (PROJECT.md §6, §7): the pattern, the country/countries it
 * resolves to, and whether that resolution is ambiguous. {@code ANY} and {@code PRIVATE}
 * patterns resolve to an empty, unambiguous region list — they are not tied to any country.
 *
 * <p>Plain class, not a {@code record} — see {@link Settings}'s class doc for why.
 */
public final class PatternDescription {

    private final Pattern pattern;
    private final List<Country> countries;
    private final boolean ambiguous;

    public PatternDescription(Pattern pattern, List<Country> countries, boolean ambiguous) {
        this.pattern = pattern;
        this.countries = countries;
        this.ambiguous = ambiguous;
    }

    public Pattern pattern() {
        return pattern;
    }

    public List<Country> countries() {
        return countries;
    }

    public boolean ambiguous() {
        return ambiguous;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PatternDescription other)) return false;
        return ambiguous == other.ambiguous
                && Objects.equals(pattern, other.pattern)
                && Objects.equals(countries, other.countries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pattern, countries, ambiguous);
    }

    @Override
    public String toString() {
        return "PatternDescription[pattern=" + pattern + ", countries=" + countries + ", ambiguous=" + ambiguous + "]";
    }
}
