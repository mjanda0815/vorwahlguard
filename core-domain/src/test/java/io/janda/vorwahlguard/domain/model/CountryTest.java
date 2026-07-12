package io.janda.vorwahlguard.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class CountryTest {

    @Test
    void flagEmojiIsTwoRegionalIndicatorCodepoints() {
        Country austria = new Country("AT", 43);

        String expected = new StringBuilder()
                .appendCodePoint(0x1F1E6 + ('A' - 'A'))
                .appendCodePoint(0x1F1E6 + ('T' - 'A'))
                .toString();

        assertThat(austria.flagEmoji()).isEqualTo(expected);
        assertThat(austria.flagEmoji().codePointCount(0, austria.flagEmoji().length())).isEqualTo(2);
    }

    @Test
    void displayNameResolvesViaLocale() {
        Country austria = new Country("AT", 43);

        assertThat(austria.displayName(Locale.GERMANY)).isEqualTo("Österreich");
        assertThat(austria.displayName(Locale.US)).isEqualTo("Austria");
    }
}
