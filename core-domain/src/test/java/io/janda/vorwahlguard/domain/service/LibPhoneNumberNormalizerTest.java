package io.janda.vorwahlguard.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.janda.vorwahlguard.domain.model.PhoneNumber;
import org.junit.jupiter.api.Test;

class LibPhoneNumberNormalizerTest {

    private final LibPhoneNumberNormalizer normalizer = new LibPhoneNumberNormalizer();

    @Test
    void nationalFormatWithDefaultRegionResolvesToE164() {
        PhoneNumber number = normalizer.normalize("0663 1234567", "AT");

        assertThat(number.isKnown()).isTrue();
        assertThat(number.e164()).isEqualTo("+436631234567");
        assertThat(number.region()).isEqualTo("AT");
    }

    @Test
    void alreadyE164InputPassesThrough() {
        PhoneNumber number = normalizer.normalize("+436631234567", "AT");

        assertThat(number.isKnown()).isTrue();
        assertThat(number.e164()).isEqualTo("+436631234567");
    }

    @Test
    void emptyInputIsUnknown() {
        assertThat(normalizer.normalize("", "AT")).isEqualTo(PhoneNumber.UNKNOWN);
        assertThat(normalizer.normalize("   ", "AT")).isEqualTo(PhoneNumber.UNKNOWN);
        assertThat(normalizer.normalize(null, "AT")).isEqualTo(PhoneNumber.UNKNOWN);
    }

    @Test
    void unparseableInputIsUnknown() {
        assertThat(normalizer.normalize("not a number", "AT")).isEqualTo(PhoneNumber.UNKNOWN);
    }

    @Test
    void tooManyDigitsIsUnknown() {
        assertThat(normalizer.normalize("+123456789012345678", "AT")).isEqualTo(PhoneNumber.UNKNOWN);
    }
}
