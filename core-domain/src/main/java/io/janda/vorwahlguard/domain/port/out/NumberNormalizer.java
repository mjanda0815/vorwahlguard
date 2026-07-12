package io.janda.vorwahlguard.domain.port.out;

import io.janda.vorwahlguard.domain.model.PhoneNumber;

/**
 * Turns raw call-handle text into a normalized {@link PhoneNumber}. Implemented inside
 * {@code :core-domain} ({@code LibPhoneNumberNormalizer}) because libphonenumber is plain
 * Java, but declared as a port so the implementation can be substituted in tests
 * (docs/ARCHITECTURE.md).
 */
public interface NumberNormalizer {

    /**
     * @param raw           the raw number text, e.g. from {@code Call.Details.getHandle()}
     * @param defaultRegion ISO-3166 alpha-2 region to resolve a national-format number
     *                      against, e.g. the SIM region
     * @return the normalized number, or {@link PhoneNumber#UNKNOWN} if {@code raw} is empty,
     *         unparseable, invalid, or exceeds the 15-digit E.164 limit
     */
    PhoneNumber normalize(String raw, String defaultRegion);
}
