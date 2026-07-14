package io.janda.vorwahlguard.domain.model;

import java.util.Objects;

/**
 * User-configurable settings (PROJECT.md §4, §7 Einstellungen). Pure data — the adapter in
 * {@code :app} decides where this is persisted (DataStore) and how it is cached for the
 * screening hot path.
 *
 * <p>Plain class, not a {@code record}: Java records desugared from this {@code java-library}
 * module lose their shared {@code RecordTag} synthetic when AGP 9.2 dexes the debug build,
 * crashing with {@code NoClassDefFoundError} the first time any record is touched (confirmed via
 * device logcat and dex inspection). Release builds are unaffected (R8's whole-program mode
 * resolves it correctly), but debug builds are broken every time, so records are not viable here
 * until that AGP gap closes.
 */
public final class Settings {

    private final boolean contactsBypassEnabled;
    private final int retentionDays;
    private final boolean pseudonymiseNumbers;
    private final boolean notifyOnBlock;

    public Settings(
            boolean contactsBypassEnabled,
            int retentionDays,
            boolean pseudonymiseNumbers,
            boolean notifyOnBlock) {
        this.contactsBypassEnabled = contactsBypassEnabled;
        this.retentionDays = retentionDays;
        this.pseudonymiseNumbers = pseudonymiseNumbers;
        this.notifyOnBlock = notifyOnBlock;
    }

    /**
     * The safest-posture defaults (CLAUDE.md §12: 90-day retention default): contacts bypass
     * off, 90-day retention, numbers not pseudonymised, no block notifications. The single
     * source of truth for these values — adapters must not redeclare them.
     */
    public static Settings defaults() {
        return new Settings(false, 90, false, false);
    }

    public boolean contactsBypassEnabled() {
        return contactsBypassEnabled;
    }

    public int retentionDays() {
        return retentionDays;
    }

    public boolean pseudonymiseNumbers() {
        return pseudonymiseNumbers;
    }

    public boolean notifyOnBlock() {
        return notifyOnBlock;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Settings other)) return false;
        return contactsBypassEnabled == other.contactsBypassEnabled
                && retentionDays == other.retentionDays
                && pseudonymiseNumbers == other.pseudonymiseNumbers
                && notifyOnBlock == other.notifyOnBlock;
    }

    @Override
    public int hashCode() {
        return Objects.hash(contactsBypassEnabled, retentionDays, pseudonymiseNumbers, notifyOnBlock);
    }

    @Override
    public String toString() {
        return "Settings[contactsBypassEnabled=" + contactsBypassEnabled
                + ", retentionDays=" + retentionDays
                + ", pseudonymiseNumbers=" + pseudonymiseNumbers
                + ", notifyOnBlock=" + notifyOnBlock
                + "]";
    }
}
