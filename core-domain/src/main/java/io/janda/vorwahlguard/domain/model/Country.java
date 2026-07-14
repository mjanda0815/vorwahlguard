package io.janda.vorwahlguard.domain.model;

import java.util.Locale;
import java.util.Objects;

/**
 * A country/region as the picker and the rule list show it: its ISO-3166 alpha-2 code and
 * its E.164 calling code. Calling code is <b>not</b> unique to this country — see
 * {@code CountryCatalog} and CLAUDE.md §6 for the 1:n resolution this asymmetry requires.
 *
 * <p>Plain class, not a {@code record} — see {@link Settings}'s class doc for why.
 */
public final class Country {

    private final String iso2;
    private final int callingCode;

    public Country(String iso2, int callingCode) {
        this.iso2 = iso2;
        this.callingCode = callingCode;
    }

    public String iso2() {
        return iso2;
    }

    public int callingCode() {
        return callingCode;
    }

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
     * <p>Uses the two-arg {@link Locale#Locale(String, String)} constructor per CLAUDE.md §6:
     * {@code Locale.of(...)} is Java 19+ and does not exist on the JDK 17 baseline this module
     * builds with, while the two-arg constructor is not deprecated on 17 and produces the
     * identical {@code Locale}.
     */
    @SuppressWarnings("deprecation")
    public String displayName(Locale locale) {
        return new Locale("", iso2).getDisplayCountry(locale);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Country other)) return false;
        return callingCode == other.callingCode && Objects.equals(iso2, other.iso2);
    }

    @Override
    public int hashCode() {
        return Objects.hash(iso2, callingCode);
    }

    @Override
    public String toString() {
        return "Country[iso2=" + iso2 + ", callingCode=" + callingCode + "]";
    }
}
