package io.janda.vorwahlguard.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import io.janda.vorwahlguard.domain.model.Settings
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * The persisted source of truth for [Settings], backed by Jetpack DataStore. Never read from
 * the `onScreenCall()` hot path (CLAUDE.md §3 rule 1) — [CachedSettingsRepository] is the
 * in-memory front for that. Missing keys fall back to the matching [Settings.defaults] field so
 * a fresh install (or a preference wiped by the user) behaves exactly like [Settings.defaults].
 */
@Singleton
class SettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val settings: Flow<Settings> = dataStore.data
        .catch { t ->
            // DataStore surfaces read failures to its collectors. An unreadable settings file
            // must degrade to defaults, not kill whichever coroutine happens to be observing —
            // a crashed screener is the worst failure mode (CLAUDE.md §3 rule 3).
            if (t is IOException) emit(emptyPreferences()) else throw t
        }
        .map { prefs -> prefs.toSettings() }

    /** The transform runs inside the [edit] transaction, so concurrent updates cannot lose one. */
    suspend fun update(transform: (Settings) -> Settings) {
        dataStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[CONTACTS_BYPASS_ENABLED] = next.contactsBypassEnabled()
            prefs[RETENTION_DAYS] = next.retentionDays()
            prefs[PSEUDONYMISE_NUMBERS] = next.pseudonymiseNumbers()
            prefs[NOTIFY_ON_BLOCK] = next.notifyOnBlock()
        }
    }

    private fun Preferences.toSettings(): Settings {
        val defaults = Settings.defaults()
        // core-domain is not compiled with -parameters, so named arguments are not available
        // here; order matches Settings' canonical constructor: contactsBypassEnabled,
        // retentionDays, pseudonymiseNumbers, notifyOnBlock.
        return Settings(
            this[CONTACTS_BYPASS_ENABLED] ?: defaults.contactsBypassEnabled(),
            this[RETENTION_DAYS] ?: defaults.retentionDays(),
            this[PSEUDONYMISE_NUMBERS] ?: defaults.pseudonymiseNumbers(),
            this[NOTIFY_ON_BLOCK] ?: defaults.notifyOnBlock(),
        )
    }

    private companion object {
        val CONTACTS_BYPASS_ENABLED = booleanPreferencesKey("contacts_bypass_enabled")
        val RETENTION_DAYS = intPreferencesKey("retention_days")
        val PSEUDONYMISE_NUMBERS = booleanPreferencesKey("pseudonymise_numbers")
        val NOTIFY_ON_BLOCK = booleanPreferencesKey("notify_on_block")
    }
}
