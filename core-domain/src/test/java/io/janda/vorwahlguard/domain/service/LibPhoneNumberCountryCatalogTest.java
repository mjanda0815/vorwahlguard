package io.janda.vorwahlguard.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.janda.vorwahlguard.domain.model.Country;
import io.janda.vorwahlguard.domain.model.Pattern;
import io.janda.vorwahlguard.domain.model.PatternDescription;
import io.janda.vorwahlguard.domain.model.PatternSyntax;
import org.junit.jupiter.api.Test;

class LibPhoneNumberCountryCatalogTest {

    private final LibPhoneNumberCountryCatalog catalog = new LibPhoneNumberCountryCatalog();

    @Test
    void byIso2ResolvesAustriaToPlus43() {
        Country austria = catalog.byIso2("AT").orElseThrow();

        assertThat(austria.callingCode()).isEqualTo(43);
    }

    @Test
    void byIso2IsCaseInsensitive() {
        assertThat(catalog.byIso2("at")).isPresent();
    }

    @Test
    void byIso2UnknownRegionIsEmpty() {
        assertThat(catalog.byIso2("XX")).isEmpty();
    }

    @Test
    void regionsForOneContainsUsAndCanadaAndHasAtLeastTwentyEntries() {
        // CLAUDE.md §6: +1 covers US, CA, and ~20 Caribbean territories. libphonenumber's
        // exact territory count may drift with metadata updates, so assert a lower bound.
        var regions = catalog.regionsFor(1);

        assertThat(regions).contains("US", "CA");
        assertThat(regions.size()).isGreaterThanOrEqualTo(20);
    }

    @Test
    void regionsForSevenIsRussiaAndKazakhstanOnly() {
        assertThat(catalog.regionsFor(7)).containsExactlyInAnyOrder("RU", "KZ");
    }

    @Test
    void regionsForFortyFourContainsUkAndCrownDependencies() {
        assertThat(catalog.regionsFor(44)).contains("GB", "JE", "GG", "IM");
    }

    @Test
    void regionsForThirtyNineContainsItalyAndVatican() {
        assertThat(catalog.regionsFor(39)).contains("IT", "VA");
    }

    @Test
    void regionsForFortyThreeIsAustriaOnly() {
        assertThat(catalog.regionsFor(43)).containsExactly("AT");
    }

    @Test
    void describeAustrianPrefixIsUnambiguous() {
        Pattern pattern = PatternSyntax.parse("+43*");

        PatternDescription description = catalog.describe(pattern);

        assertThat(description.ambiguous()).isFalse();
        assertThat(description.countries()).extracting(Country::iso2).containsExactly("AT");
    }

    @Test
    void describePlusOnePrefixIsAmbiguous() {
        Pattern pattern = PatternSyntax.parse("+1*");

        PatternDescription description = catalog.describe(pattern);

        assertThat(description.ambiguous()).isTrue();
        assertThat(description.countries().size()).isGreaterThanOrEqualTo(20);
    }

    @Test
    void describeExactAustrianNumberResolvesToSingleRegion() {
        Pattern pattern = PatternSyntax.parse("+436631234567");

        PatternDescription description = catalog.describe(pattern);

        assertThat(description.ambiguous()).isFalse();
        assertThat(description.countries()).hasSize(1);
        assertThat(description.countries().get(0).iso2()).isEqualTo("AT");
        assertThat(description.countries().get(0).callingCode()).isEqualTo(43);
    }

    @Test
    void describeExactNumberWithSharedCallingCodeResolvesToOneRegionNotWholeGroup() {
        // describePlusOnePrefixIsAmbiguous (above) shows the bare +1* PREFIX is ambiguous
        // across 20+ regions — but one specific EXACT number under +1 must resolve to exactly
        // the single region it actually belongs to, not the whole calling-code group.
        Pattern pattern = PatternSyntax.parse("+14165551234"); // Toronto, ON area code

        PatternDescription description = catalog.describe(pattern);

        assertThat(description.ambiguous()).isFalse();
        assertThat(description.countries()).hasSize(1);
        assertThat(description.countries().get(0).iso2()).isEqualTo("CA");
    }

    @Test
    void describePrivateResolvesToNoRegions() {
        PatternDescription description = catalog.describe(PatternSyntax.parse("PRIVATE"));

        assertThat(description.countries()).isEmpty();
        assertThat(description.ambiguous()).isFalse();
    }

    @Test
    void describeBareStarResolvesToNoRegions() {
        PatternDescription description = catalog.describe(PatternSyntax.parse("*"));

        assertThat(description.countries()).isEmpty();
        assertThat(description.ambiguous()).isFalse();
    }

    @Test
    void flagEmojiForAustriaIsCorrectTwoCodepointString() {
        Country austria = catalog.byIso2("AT").orElseThrow();

        String flag = austria.flagEmoji();

        assertThat(flag.codePointCount(0, flag.length())).isEqualTo(2);
        assertThat(flag.codePointAt(0)).isEqualTo(0x1F1E6); // regional indicator 'A'
        assertThat(flag.codePointAt(flag.offsetByCodePoints(0, 1))).isEqualTo(0x1F1F9); // 'T'
    }
}
