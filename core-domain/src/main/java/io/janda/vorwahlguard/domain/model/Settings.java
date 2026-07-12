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
}
