package io.janda.vorwahlguard.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PhoneNumberTest {

    @Test
    void unknownSentinelHasNoUsableFields() {
        assertThat(PhoneNumber.UNKNOWN.raw()).isNull();
        assertThat(PhoneNumber.UNKNOWN.e164()).isNull();
        assertThat(PhoneNumber.UNKNOWN.region()).isNull();
    }

    @Test
    void unknownSentinelIsUnknown() {
        assertThat(PhoneNumber.UNKNOWN.isUnknown()).isTrue();
        assertThat(PhoneNumber.UNKNOWN.isKnown()).isFalse();
    }

    @Test
    void numberWithE164IsKnown() {
        PhoneNumber number = new PhoneNumber("0663 1234567", "+436631234567", "AT");

        assertThat(number.isKnown()).isTrue();
        assertThat(number.isUnknown()).isFalse();
    }

    @Test
    void equalValuesAreEqual() {
        PhoneNumber a = new PhoneNumber("raw", "+436631234567", "AT");
        PhoneNumber b = new PhoneNumber("raw", "+436631234567", "AT");

        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
    }
}
