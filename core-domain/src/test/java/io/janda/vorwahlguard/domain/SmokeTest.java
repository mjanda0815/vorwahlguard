package io.janda.vorwahlguard.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import org.junit.jupiter.api.Test;

/**
 * M0 smoke test: proves the module is wired up before any real domain logic exists.
 * It exercises the JUnit 5 + AssertJ test path and confirms libphonenumber is on the
 * classpath — using the one fact this whole app is built around: +43 is Austria.
 */
class SmokeTest {

    @Test
    void libphonenumberResolvesAustrianCallingCode() {
        assertThat(PhoneNumberUtil.getInstance().getCountryCodeForRegion("AT")).isEqualTo(43);
    }

    @Test
    void assertjAndJupiterAreWired() {
        assertThat(1 + 1).isEqualTo(2);
    }
}
