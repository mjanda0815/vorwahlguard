package io.janda.vorwahlguard.domain.model;

/**
 * User-configurable settings (PROJECT.md §4, §7 Einstellungen). Pure data — the adapter in
 * {@code :app} decides where this is persisted (DataStore) and how it is cached for the
 * screening hot path.
 */
public record Settings(
        boolean contactsBypassEnabled,
        int retentionDays,
        boolean pseudonymiseNumbers,
        boolean notifyOnBlock) {

    /**
     * The safest-posture defaults (CLAUDE.md §12: 90-day retention default): contacts bypass
     * off, 90-day retention, numbers not pseudonymised, no block notifications. The single
     * source of truth for these values — adapters must not redeclare them.
     */
    public static Settings defaults() {
        return new Settings(false, 90, false, false);
    }
}
