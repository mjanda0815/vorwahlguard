package io.janda.vorwahlguard.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PatternTest {

    private static final PhoneNumber KNOWN = new PhoneNumber("raw", "+436631234567", "AT");
    private static final PhoneNumber OTHER_KNOWN = new PhoneNumber("raw", "+491701234567", "DE");

    @Test
    void exactMatchesOnlyEqualE164() {
        Pattern exact = PatternSyntax.parse("+436631234567");

        assertThat(exact.matches(KNOWN)).isTrue();
        assertThat(exact.matches(new PhoneNumber("r", "+436631234568", "AT"))).isFalse();
        assertThat(exact.matches(PhoneNumber.UNKNOWN)).isFalse();
    }

    @Test
    void prefixMatchesByPrefix() {
        Pattern prefix = PatternSyntax.parse("+4366*");

        assertThat(prefix.matches(KNOWN)).isTrue();
        assertThat(prefix.matches(OTHER_KNOWN)).isFalse();
        assertThat(prefix.matches(PhoneNumber.UNKNOWN)).isFalse();
    }

    @Test
    void anyMatchesAnyKnownNumberButNotUnknown() {
        Pattern any = PatternSyntax.parse("*");

        assertThat(any.matches(KNOWN)).isTrue();
        assertThat(any.matches(OTHER_KNOWN)).isTrue();
        assertThat(any.matches(PhoneNumber.UNKNOWN)).isFalse();
    }

    @Test
    void privateMatchesUnknownOnly() {
        Pattern privatePattern = PatternSyntax.parse("PRIVATE");

        assertThat(privatePattern.matches(PhoneNumber.UNKNOWN)).isTrue();
        assertThat(privatePattern.matches(KNOWN)).isFalse();
    }

    @Test
    void specificityOrdersExactOverLongerOverShorterOverAny() {
        int exact = PatternSyntax.parse("+436631234567").specificity();
        int longerPrefix = PatternSyntax.parse("+4366312*").specificity();
        int shorterPrefix = PatternSyntax.parse("+43*").specificity();
        int any = PatternSyntax.parse("*").specificity();

        assertThat(exact).isGreaterThan(longerPrefix);
        assertThat(longerPrefix).isGreaterThan(shorterPrefix);
        assertThat(shorterPrefix).isGreaterThan(any);
    }
}
