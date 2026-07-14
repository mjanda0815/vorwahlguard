package io.janda.vorwahlguard.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.janda.vorwahlguard.domain.model.Settings
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * [CachedSettingsRepository] against a real [SettingsStore] on a temp-file DataStore. [current]
 * must be safe to call from the `onScreenCall()` hot path (CLAUDE.md §3 rule 1), so it never
 * does I/O itself — [refresh] and [observeAndCache] are the only places that touch the store.
 */
class CachedSettingsRepositoryTest {

    private lateinit var file: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: SettingsStore
    private lateinit var repository: CachedSettingsRepository

    @Before
    fun setUp() {
        file = File.createTempFile("cached-settings-repository-test", ".preferences_pb")
        file.delete()
        dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        store = SettingsStore(dataStore)
        repository = CachedSettingsRepository(store)
    }

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun `current returns Settings defaults before the first refresh`() {
        assertEquals(Settings.defaults(), repository.current())
    }

    @Test
    fun `current reflects the store after refresh`() = runBlocking {
        val nonDefault = Settings(true, 30, true, true, true)
        store.update { nonDefault }

        repository.refresh()

        assertEquals(nonDefault, repository.current())
    }

    @Test
    fun `observeAndCache picks up a later update`() = runBlocking {
        // Unconfined (CLAUDE.md app test convention, see VorwahlGuardScreeningServiceTest): the
        // collector resumes inline on whatever thread the DataStore actor notifies it from,
        // instead of racing a separate Dispatchers.Default thread pool against a wall-clock
        // timeout.
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        scope.launch { repository.observeAndCache() }

        val nonDefault = Settings(true, 30, true, true, true)
        store.update { nonDefault }

        awaitCurrent(nonDefault)

        scope.cancel()
    }

    /**
     * Polls the in-memory snapshot, bounded by a timeout, since [observeAndCache] runs forever.
     * [delay] (not a busy-spinning [kotlinx.coroutines.yield]) so this genuinely parks the
     * thread between checks instead of competing for CPU with the collector it is waiting on.
     */
    private suspend fun awaitCurrent(expected: Settings) {
        withTimeout(10_000) {
            while (repository.current() != expected) {
                delay(10)
            }
        }
    }
}
