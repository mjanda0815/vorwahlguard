package io.janda.vorwahlguard.domain.service;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import io.janda.vorwahlguard.domain.model.Country;
import io.janda.vorwahlguard.domain.model.Pattern;
import io.janda.vorwahlguard.domain.model.PatternDescription;
import io.janda.vorwahlguard.domain.model.PatternKind;
import io.janda.vorwahlguard.domain.port.out.CountryCatalog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * {@link CountryCatalog} backed by libphonenumber.
 *
 * <p><b>ADR 0007 follow-up:</b> that ADR assumed {@code PhoneNumberUtil.getRegionCodesForCountryCode(int)}
 * does not exist in the pinned {@code 9.0.34} release, based on a source search it explicitly
 * flagged as unproven, and specified a {@code getSupportedRegions()} fallback pending an actual
 * compile attempt. That compile attempt (this class) shows the method <b>does</b> exist and is
 * public — {@code javap} against the resolved {@code libphonenumber-9.0.34.jar} confirms
 * {@code public java.util.List<java.lang.String> getRegionCodesForCountryCode(int)}, and it is
 * used directly below. ADR 0007's decision section should be revisited; this comment exists so
 * that follow-up is not lost. See also the M1 implementation report for this milestone.
 */
public final class LibPhoneNumberCountryCatalog implements CountryCatalog {

    private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
    private final Map<Integer, List<String>> regionsByCallingCode = new ConcurrentHashMap<>();

    @Override
    public List<Country> all() {
        return phoneNumberUtil.getSupportedRegions().stream()
                .map(region -> new Country(region, phoneNumberUtil.getCountryCodeForRegion(region)))
                .sorted(Comparator.comparing(Country::iso2))
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public Optional<Country> byIso2(String iso2) {
        if (iso2 == null) {
            return Optional.empty();
        }
        String upper = iso2.toUpperCase(Locale.ROOT);
        if (!phoneNumberUtil.getSupportedRegions().contains(upper)) {
            return Optional.empty();
        }
        return Optional.of(new Country(upper, phoneNumberUtil.getCountryCodeForRegion(upper)));
    }

    @Override
    public List<String> regionsFor(int callingCode) {
        return regionsByCallingCode.computeIfAbsent(callingCode, this::loadRegions);
    }

    private List<String> loadRegions(int callingCode) {
        List<String> regions = new ArrayList<>(phoneNumberUtil.getRegionCodesForCountryCode(callingCode));
        Collections.sort(regions);
        return Collections.unmodifiableList(regions);
    }

    @Override
    public PatternDescription describe(Pattern pattern) {
        if (pattern.kind() == PatternKind.ANY || pattern.kind() == PatternKind.PRIVATE) {
            return new PatternDescription(pattern, List.of(), false);
        }
        if (pattern.kind() == PatternKind.EXACT) {
            return describeExact(pattern);
        }
        return describeCallingCodeGroup(pattern);
    }

    /**
     * An EXACT pattern is one specific number, not a calling-code group: it resolves to at
     * most one region, never to the whole {@code regionsFor(callingCode)} set. Uses
     * libphonenumber's number-level {@link PhoneNumberUtil#getRegionCodeForNumber} rather than
     * the calling-code-prefix heuristic {@link #extractCallingCode(String)} uses, since a
     * single number (unlike a bare prefix) can be parsed and attributed unambiguously.
     */
    private PatternDescription describeExact(Pattern pattern) {
        try {
            com.google.i18n.phonenumbers.Phonenumber.PhoneNumber parsed =
                    phoneNumberUtil.parse(pattern.text(), null);
            String region = phoneNumberUtil.getRegionCodeForNumber(parsed);
            if (region == null) {
                // No single region for this exact number (e.g. a non-geographic calling
                // code) — fall back to describing it by its calling-code group.
                return describeCallingCodeGroup(pattern);
            }
            Country country = new Country(region, parsed.getCountryCode());
            return new PatternDescription(pattern, List.of(country), false);
        } catch (NumberParseException e) {
            return describeCallingCodeGroup(pattern);
        }
    }

    private PatternDescription describeCallingCodeGroup(Pattern pattern) {
        int callingCode = extractCallingCode(pattern.digits());
        if (callingCode <= 0) {
            return new PatternDescription(pattern, List.of(), false);
        }

        List<String> regions = regionsFor(callingCode);
        List<Country> countries = regions.stream()
                .map(region -> new Country(region, callingCode))
                .collect(Collectors.toUnmodifiableList());
        return new PatternDescription(pattern, countries, countries.size() > 1);
    }

    /**
     * Calling codes are 1-3 digits and, by ITU design, prefix-free — no valid calling code is
     * itself a prefix of another. Try the longest candidate first purely as a safety margin
     * against that assumption, falling back to shorter ones.
     */
    private int extractCallingCode(String digits) {
        if (digits == null || digits.isEmpty()) {
            return -1;
        }
        Set<Integer> knownCallingCodes = phoneNumberUtil.getSupportedCallingCodes();
        int maxLength = Math.min(3, digits.length());
        for (int length = maxLength; length >= 1; length--) {
            int candidate = Integer.parseInt(digits.substring(0, length));
            if (knownCallingCodes.contains(candidate)) {
                return candidate;
            }
        }
        return -1;
    }
}
