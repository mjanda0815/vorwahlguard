package io.janda.vorwahlguard.domain.port.out;

import io.janda.vorwahlguard.domain.model.Country;
import io.janda.vorwahlguard.domain.model.Pattern;
import io.janda.vorwahlguard.domain.model.PatternDescription;
import java.util.List;
import java.util.Optional;

/**
 * Country ↔ calling-code resolution, including the CLAUDE.md §6 1:n asymmetry
 * (calling code → country can be ambiguous, country → calling code never is). Implemented
 * inside {@code :core-domain} ({@code LibPhoneNumberCountryCatalog}) since libphonenumber is
 * plain Java, but declared as a port so a future replacement does not ripple into `:app`.
 */
public interface CountryCatalog {

    /** Every supported country/region, for the picker. */
    List<Country> all();

    /** {@code "AT"} -&gt; {@code +43}. Country → calling code is always 1:1. */
    Optional<Country> byIso2(String iso2);

    /** {@code 1} -&gt; {@code [US, CA, BS, ...]}. Calling code → country can be 1:n. */
    List<String> regionsFor(int callingCode);

    /** Read model for the rule list: resolved regions plus the ambiguity flag. */
    PatternDescription describe(Pattern pattern);
}
