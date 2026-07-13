package io.janda.vorwahlguard.ui.einstellungen

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.janda.vorwahlguard.data.AppInfoProvider
import io.janda.vorwahlguard.data.contacts.CachedContactsLookup
import io.janda.vorwahlguard.data.contacts.ContactsPermissionState
import io.janda.vorwahlguard.data.settings.SettingsStore
import io.janda.vorwahlguard.di.IoDispatcher
import io.janda.vorwahlguard.domain.model.Settings
import io.janda.vorwahlguard.screening.CallScreeningRoleProvider
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backs `EinstellungenScreen` (issue #29). Owns [SettingsStore] read/write, the `READ_CONTACTS`
 * grant check ([ContactsPermissionState]) that gates [onContactsBypassToggled], the role status
 * ([CallScreeningRoleProvider], re-read on `ON_RESUME` via [refreshRoleStatus] — the same split
 * `UebersichtViewModel` uses) and the app version for the About section. Every settings write
 * goes through [SettingsStore.update]'s read-modify-write transform, mirroring
 * [io.janda.vorwahlguard.ui.regeln.addrule.AddRuleViewModel]'s "ViewModel talks to the adapter
 * directly, no extra layer" style — there is no dedicated settings-repository port for the UI
 * side, [CachedSettingsRepository][io.janda.vorwahlguard.data.settings.CachedSettingsRepository]
 * exists only for the `onScreenCall()` hot path (CLAUDE.md §3 rule 1).
 */
@HiltViewModel
class EinstellungenViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val contactsLookup: CachedContactsLookup,
    private val permissionState: ContactsPermissionState,
    private val roleProvider: CallScreeningRoleProvider,
    appInfoProvider: AppInfoProvider,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        EinstellungenUiState(
            appVersion = appInfoProvider.versionName(),
            roleAvailable = roleProvider.isRoleAvailable(),
            roleHeld = roleProvider.isRoleHeld(),
        ),
    )
    val uiState: StateFlow<EinstellungenUiState> = _uiState.asStateFlow()

    private val _effects = Channel<EinstellungenEffect>(Channel.BUFFERED)
    val effects: Flow<EinstellungenEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        loaded = true,
                        contactsBypassEnabled = settings.contactsBypassEnabled(),
                        pseudonymiseNumbers = settings.pseudonymiseNumbers(),
                        notifyOnBlock = settings.notifyOnBlock(),
                        retentionDays = settings.retentionDays(),
                    )
                }
            }
        }
    }

    /**
     * Disabling never needs the permission. Enabling requires `READ_CONTACTS`: if it is already
     * granted, persist and immediately [CachedContactsLookup.refresh] the cache; otherwise emit
     * [EinstellungenEffect.RequestContactsPermission] and let [onContactsPermissionResult] finish
     * the job once the system prompt returns.
     */
    fun onContactsBypassToggled(desired: Boolean) {
        _uiState.update { it.copy(contactsPermissionDenied = false) }
        if (!desired) {
            viewModelScope.launch {
                settingsStore.update { withContactsBypass(it, false) }
            }
            return
        }
        if (permissionState.isGranted()) {
            viewModelScope.launch {
                settingsStore.update { withContactsBypass(it, true) }
                withContext(ioDispatcher) { contactsLookup.refresh() }
            }
        } else {
            viewModelScope.launch { _effects.send(EinstellungenEffect.RequestContactsPermission) }
        }
    }

    /** Result of the `READ_CONTACTS` system prompt launched in response to [onContactsBypassToggled]. */
    fun onContactsPermissionResult(granted: Boolean) {
        if (granted) {
            viewModelScope.launch {
                settingsStore.update { withContactsBypass(it, true) }
                withContext(ioDispatcher) { contactsLookup.refresh() }
            }
            _uiState.update { it.copy(contactsPermissionDenied = false) }
        } else {
            _uiState.update { it.copy(contactsPermissionDenied = true) }
        }
    }

    fun onPseudonymiseToggled(value: Boolean) {
        viewModelScope.launch {
            settingsStore.update { Settings(it.contactsBypassEnabled(), it.retentionDays(), value, it.notifyOnBlock()) }
        }
    }

    fun onNotifyOnBlockToggled(value: Boolean) {
        viewModelScope.launch {
            settingsStore.update { Settings(it.contactsBypassEnabled(), it.retentionDays(), it.pseudonymiseNumbers(), value) }
        }
    }

    fun onRetentionDaysSelected(days: Int) {
        viewModelScope.launch {
            settingsStore.update { Settings(it.contactsBypassEnabled(), days, it.pseudonymiseNumbers(), it.notifyOnBlock()) }
        }
    }

    /** Re-checks role status without touching the settings Flow — call on `ON_RESUME`. */
    fun refreshRoleStatus() {
        _uiState.update {
            it.copy(roleAvailable = roleProvider.isRoleAvailable(), roleHeld = roleProvider.isRoleHeld())
        }
    }

    fun createRoleRequestIntent(): Intent? = roleProvider.createRoleRequestIntent()

    /**
     * core-domain is not compiled with `-parameters`, so named arguments are not available here —
     * order matches [Settings]' canonical constructor: `contactsBypassEnabled, retentionDays,
     * pseudonymiseNumbers, notifyOnBlock` (same order [SettingsStore] itself relies on).
     */
    private fun withContactsBypass(current: Settings, value: Boolean) =
        Settings(value, current.retentionDays(), current.pseudonymiseNumbers(), current.notifyOnBlock())
}
