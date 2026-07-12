package io.janda.vorwahlguard.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PatternDescriptionTest {

    @Test
    void isPlainImmutableData() {
        Pattern pattern = PatternSyntax.parse("+43*");
        Country austria = new Country("AT", 43);
        PatternDescription description = new PatternDescription(pattern, List.of(austria), false);

        assertThat(description.pattern()).isEqualTo(pattern);
        assertThat(description.countries()).containsExactly(austria);
        assertThat(description.ambiguous()).isFalse();
    }

    @Test
    void ambiguousWhenMultipleCountriesResolve() {
        Pattern pattern = PatternSyntax.parse("+1*");
        PatternDescription description = new PatternDescription(
                pattern, List.of(new Country("US", 1), new Country("CA", 1)), true);

        assertThat(description.ambiguous()).isTrue();
        assertThat(description.countries()).hasSize(2);
    }
}
