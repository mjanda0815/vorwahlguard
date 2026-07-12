package io.janda.vorwahlguard.domain.model;

import java.util.List;

/**
 * Read model for the rule list (PROJECT.md §6, §7): the pattern, the country/countries it
 * resolves to, and whether that resolution is ambiguous. {@code ANY} and {@code PRIVATE}
 * patterns resolve to an empty, unambiguous region list — they are not tied to any country.
 */
public record PatternDescription(Pattern pattern, List<Country> countries, boolean ambiguous) {
}
