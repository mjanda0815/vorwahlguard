package io.janda.vorwahlguard.domain.service;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import io.janda.vorwahlguard.domain.model.PhoneNumber;
import io.janda.vorwahlguard.domain.port.out.NumberNormalizer;

/**
 * {@link NumberNormalizer} backed by libphonenumber's core artifact only — no
 * {@code geocoder}/{@code carrier} (CLAUDE.md §2). National-format input plus a default
 * region resolve to E.164; anything that cannot be parsed into a valid number collapses to
 * {@link PhoneNumber#UNKNOWN} rather than throwing, so callers on the screening hot path
 * never have to handle a checked exception (CLAUDE.md §3 rule 3 keeps that guarantee at the
 * service boundary, not here — but never throwing keeps this class consistent with it).
 */
public final class LibPhoneNumberNormalizer implements NumberNormalizer {

    private static final int MAX_E164_DIGITS = 15;

    private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

    @Override
    public PhoneNumber normalize(String raw, String defaultRegion) {
        if (raw == null || raw.isBlank()) {
            return PhoneNumber.UNKNOWN;
        }
        try {
            com.google.i18n.phonenumbers.Phonenumber.PhoneNumber parsed =
                    phoneNumberUtil.parse(raw, defaultRegion);
            if (!phoneNumberUtil.isValidNumber(parsed)) {
                return PhoneNumber.UNKNOWN;
            }
            String e164 = phoneNumberUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
            if (e164.length() - 1 > MAX_E164_DIGITS) {
                return PhoneNumber.UNKNOWN;
            }
            String region = phoneNumberUtil.getRegionCodeForNumber(parsed);
            return new PhoneNumber(raw, e164, region);
        } catch (NumberParseException e) {
            return PhoneNumber.UNKNOWN;
        }
    }
}
