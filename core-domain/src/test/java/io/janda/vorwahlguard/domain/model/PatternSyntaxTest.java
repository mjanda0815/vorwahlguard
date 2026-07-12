package io.janda.vorwahlguard.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class PatternSyntaxTest {

    @Test
    void bareStarIsValidAny() {
        assertThat(PatternSyntax.isValid("*")).isTrue();
        assertThat(PatternSyntax.parse("*").kind()).isEqualTo(PatternKind.ANY);
    }

    @Test
    void countryPrefixIsValid() {
        assertThat(PatternSyntax.isValid("+43*")).isTrue();
        assertThat(PatternSyntax.parse("+43*").kind()).isEqualTo(PatternKind.PREFIX);
    }

    @Test
    void exactE164NumberIsValid() {
        assertThat(PatternSyntax.isValid("+436631234567")).isTrue();
        assertThat(PatternSyntax.parse("+436631234567").kind()).isEqualTo(PatternKind.EXACT);
    }

    @Test
    void multipleWildcardsAreInvalid() {
        assertThat(PatternSyntax.isValid("+43**")).isFalse();
    }

    @Test
    void infixWildcardIsInvalid() {
        assertThat(PatternSyntax.isValid("+4*3")).isFalse();
    }

    @Test
    void leadingWildcardThatIsNotBareStarIsInvalid() {
        assertThat(PatternSyntax.isValid("*43")).isFalse();
    }

    @Test
    void regexMetacharactersAreInvalid() {
        assertThat(PatternSyntax.isValid("+43[0-9]*")).isFalse();
        assertThat(PatternSyntax.isValid("+43?")).isFalse();
        assertThat(PatternSyntax.isValid("+43(663)*")).isFalse();
    }

    @Test
    void wildcardFreePatternWithoutLeadingPlusIsInvalid() {
        assertThat(PatternSyntax.isValid("436631234567")).isFalse();
    }

    @Test
    void wildcardFreePatternWithLettersIsInvalid() {
        assertThat(PatternSyntax.isValid("+436A6312345")).isFalse();
    }

    @Test
    void wildcardFreePatternExceedingFifteenDigitsIsInvalid() {
        assertThat(PatternSyntax.isValid("+4366312345678901")).isFalse();
    }

    @Test
    void wildcardFreePatternWithLeadingZeroIsInvalid() {
        // No real E.164 calling code starts with '0' (CLAUDE.md §4).
        assertThat(PatternSyntax.isValid("+0123456")).isFalse();
    }

    @Test
    void prefixPatternWithLeadingZeroIsInvalid() {
        // The leading digit of a PREFIX pattern is still a calling-code digit and must be
        // held to the same "no leading zero" rule as an EXACT pattern.
        assertThat(PatternSyntax.isValid("+0*")).isFalse();
    }

    @Test
    void reservedPrivateTokenIsValid() {
        assertThat(PatternSyntax.isValid("PRIVATE")).isTrue();
        assertThat(PatternSyntax.parse("PRIVATE").kind()).isEqualTo(PatternKind.PRIVATE);
    }

    @Test
    void lowercasePrivateIsInvalid() {
        assertThat(PatternSyntax.isValid("private")).isFalse();
    }

    @Test
    void emptyStringIsInvalid() {
        assertThat(PatternSyntax.isValid("")).isFalse();
    }

    @Test
    void nullIsInvalid() {
        assertThat(PatternSyntax.isValid(null)).isFalse();
    }

    @Test
    void parseThrowsOnInvalidPattern() {
        assertThatIllegalArgumentException().isThrownBy(() -> PatternSyntax.parse("+43**"));
    }
}
