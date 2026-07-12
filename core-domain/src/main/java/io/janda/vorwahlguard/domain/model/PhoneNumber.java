package io.janda.vorwahlguard.domain.model;

/**
 * Immutable value object for a phone number as the domain sees it: the raw text the
 * adapter received, the normalized E.164 form (if it could be parsed), and the ISO-3166
 * alpha-2 region libphonenumber attributed it to.
 *
 * <p>{@link #UNKNOWN} is the sentinel for a withheld caller ID ({@code Call.Details.getHandle()
 * == null}, CLAUDE.md §3 rule 2) and for numbers that could not be normalized at all — both
 * cases collapse to the same "no usable number" state as far as the domain is concerned.
 */
public record PhoneNumber(String raw, String e164, String region) {

    public static final PhoneNumber UNKNOWN = new PhoneNumber(null, null, null);

    public boolean isKnown() {
        return e164 != null;
    }

    public boolean isUnknown() {
        return !isKnown();
    }
}
