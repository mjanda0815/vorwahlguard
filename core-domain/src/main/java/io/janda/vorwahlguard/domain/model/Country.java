package io.janda.vorwahlguard.domain.model;

import java.util.Locale;

/**
 * A country/region as the picker and the rule list show it: its ISO-3166 alpha-2 code and
 * its E.164 calling code. Calling code is <b>not</b> unique to this country — see
 * {@code CountryCatalog} and CLAUDE.md §6 for the 1:n resolution this asymmetry requires.
 */
public record Country(String iso2, int callingCode) {

    /**
     * Two regional-indicator codepoints, {@code 0x1F1E6 + (c - 'A')} per ISO2 letter
     * (CLAUDE.md §6). Computed, not shipped as an asset — works offline.
     */
    public String flagEmoji() {
        String upper = iso2.toUpperCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < upper.length(); i++) {
            char c = upper.charAt(i);
            builder.appendCodePoint(0x1F1E6 + (c - 'A'));
        }
        return builder.toString();
    }

    /**
     * Localized display name via {@link Locale}, never a hardcoded country list.
     *
     * <p>CLAUDE.md §6 specifies {@code Locale.of("", iso2)} — that factory method is Java 19+
     * and does not exist on the JDK 17 baseline this module builds with (CLAUDE.md §2
     * pins JDK 17 "not 21, not 25"; confirmed by {@code javap java.util.Locale} against the
     * installed Temurin 17.0.19: no {@code of(...)} method). This uses the two-arg
     * {@link Locale#Locale(String, String)} constructor instead, which is not deprecated on
     * Java 17 and produces the identical {@code Locale}. Flagged here per CLAUDE.md §10
     * ("if something in this file contradicts reality ... say so") rather than silently
     * diverging from the spec text.
     */
    @SuppressWarnings("deprecation")
    public String displayName(Locale locale) {
        return new Locale("", iso2).getDisplayCountry(locale);
    }
}
