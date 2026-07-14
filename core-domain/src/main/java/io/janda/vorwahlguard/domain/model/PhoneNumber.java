package io.janda.vorwahlguard.domain.model;

import java.util.Objects;

/**
 * Immutable value object for a phone number as the domain sees it: the raw text the
 * adapter received, the normalized E.164 form (if it could be parsed), and the ISO-3166
 * alpha-2 region libphonenumber attributed it to.
 *
 * <p>{@link #UNKNOWN} is the sentinel for a withheld caller ID ({@code Call.Details.getHandle()
 * == null}, CLAUDE.md §3 rule 2) and for numbers that could not be normalized at all — both
 * cases collapse to the same "no usable number" state as far as the domain is concerned.
 *
 * <p>Plain class, not a {@code record} — see {@link Settings}'s class doc for why.
 */
public final class PhoneNumber {

    public static final PhoneNumber UNKNOWN = new PhoneNumber(null, null, null);

    private final String raw;
    private final String e164;
    private final String region;

    public PhoneNumber(String raw, String e164, String region) {
        this.raw = raw;
        this.e164 = e164;
        this.region = region;
    }

    public String raw() {
        return raw;
    }

    public String e164() {
        return e164;
    }

    public String region() {
        return region;
    }

    public boolean isKnown() {
        return e164 != null;
    }

    public boolean isUnknown() {
        return !isKnown();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PhoneNumber other)) return false;
        return Objects.equals(raw, other.raw)
                && Objects.equals(e164, other.e164)
                && Objects.equals(region, other.region);
    }

    @Override
    public int hashCode() {
        return Objects.hash(raw, e164, region);
    }

    @Override
    public String toString() {
        return "PhoneNumber[raw=" + raw + ", e164=" + e164 + ", region=" + region + "]";
    }
}
