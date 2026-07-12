package io.janda.vorwahlguard.data.events

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The device-local salt for `sha256(number + salt)` pseudonymisation (CLAUDE.md §12). Created
 * lazily on first use inside a single `edit {}` transaction so two concurrent first-writers
 * cannot race two different salts into existence; memory-cached after that so later lookups
 * never touch DataStore again.
 */
@Singleton
class PseudonymSaltProvider @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    private val mutex = Mutex()

    @Volatile
    private var cached: String? = null

    suspend fun salt(): String {
        cached?.let { return it }
        return mutex.withLock {
            cached?.let { return it }
            val prefs = dataStore.edit { prefs ->
                if (prefs[SALT_KEY] == null) {
                    prefs[SALT_KEY] = generateSalt()
                }
            }
            val resolved = requireNotNull(prefs[SALT_KEY]) { "salt must be present after edit" }
            cached = resolved
            resolved
        }
    }

    private fun generateSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    private companion object {
        const val SALT_BYTES = 32
        val SALT_KEY = stringPreferencesKey("pseudonym_salt")
    }
}
