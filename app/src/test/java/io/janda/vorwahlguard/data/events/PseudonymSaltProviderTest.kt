package io.janda.vorwahlguard.data.events

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [PseudonymSaltProvider] against a real temp-file-backed DataStore — plain JVM. The salt must
 * survive both repeated in-memory lookups (the [PseudonymSaltProvider.cached] fast path) and a
 * fresh provider instance reading the same underlying store (the lazy-create-once contract).
 */
class PseudonymSaltProviderTest {

    private lateinit var file: File
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        file = File.createTempFile("pseudonym-salt-test", ".preferences_pb")
        file.delete()
        dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
    }

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun `a salt is created lazily on first access`() = runBlocking {
        val salt = PseudonymSaltProvider(dataStore).salt()

        assertTrue(salt.isNotEmpty())
    }

    @Test
    fun `repeated calls on the same provider instance return a stable salt`() = runBlocking {
        val provider = PseudonymSaltProvider(dataStore)

        val first = provider.salt()
        val second = provider.salt()

        assertEquals(first, second)
    }

    @Test
    fun `a fresh DataStore instance over the same file sees the already-persisted salt`() = runBlocking {
        // Two sequential DataStore instances on their own file (not setUp's, which stays active):
        // the first is shut down via its scope before the second binds the file, so the second
        // read can only succeed if the salt was genuinely persisted, not served from memory.
        val ownFile = File.createTempFile("pseudonym-salt-reopen-test", ".preferences_pb")
        ownFile.delete()
        try {
            val firstJob = SupervisorJob()
            val firstStore = PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.IO + firstJob),
                produceFile = { ownFile },
            )
            val first = PseudonymSaltProvider(firstStore).salt()
            firstJob.cancel()
            firstJob.join()

            val secondJob = SupervisorJob()
            val secondStore = PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.IO + secondJob),
                produceFile = { ownFile },
            )
            val second = PseudonymSaltProvider(secondStore).salt()
            secondJob.cancel()

            assertEquals(first, second)
        } finally {
            ownFile.delete()
        }
    }
}
