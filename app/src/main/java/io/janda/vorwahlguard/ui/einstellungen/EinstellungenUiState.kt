package io.janda.vorwahlguard.ui.einstellungen

/**
 * State for the Einstellungen screen (issue #29). [loaded] distinguishes "not loaded yet" from
 * "loaded with defaults" the same way [io.janda.vorwahlguard.ui.protokoll.ProtokollUiState] and
 * [io.janda.vorwahlguard.ui.regeln.RegelnUiState] do: it flips once the first
 * [io.janda.vorwahlguard.data.settings.SettingsStore.settings] emission has been applied.
 * [roleAvailable]/[roleHeld], [appVersion] and [copyrightYear] are independent of [loaded] — they
 * are read eagerly at construction (and [roleAvailable]/[roleHeld] again on `ON_RESUME`), not
 * derived from the settings Flow. [contactsPermissionDenied] is UI-only transient state: it is set
 * when the user declines the `READ_CONTACTS` system prompt and is never persisted.
 */
data class EinstellungenUiState(
    val loaded: Boolean = false,
    val roleAvailable: Boolean = false,
    val roleHeld: Boolean = false,
    val contactsBypassEnabled: Boolean = false,
    val pseudonymiseNumbers: Boolean = false,
    val notifyOnBlock: Boolean = false,
    val retentionDays: Int = 90,
    val contactsPermissionDenied: Boolean = false,
    val appVersion: String = "",
    val copyrightYear: Int = 0,
)

/** One-shot side effects [EinstellungenScreen] must act on outside of recomposition. */
sealed interface EinstellungenEffect {
    /** The contacts-bypass toggle needs `READ_CONTACTS`, which is not currently granted. */
    data object RequestContactsPermission : EinstellungenEffect
}
