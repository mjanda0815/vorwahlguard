package io.janda.vorwahlguard.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.janda.vorwahlguard.domain.model.Settings
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * [SettingsStore] against a real Jetpack DataStore backed by a temp file — plain JVM, no
 * Robolectric needed, since [PreferenceDataStoreFactory.create] only needs a `produceFile`
 * lambda. A fresh file must behave exactly like [Settings.defaults] (CLAUDE.md §12).
 */
class SettingsStoreTest {

    private lateinit var file: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: SettingsStore

    @Before
    fun setUp() {
        file = File.createTempFile("settings-store-test", ".preferences_pb")
        file.delete()
        dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        store = SettingsStore(dataStore)
    }

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun `a fresh store with no written keys reports Settings defaults`() = runBlocking {
        val settings = store.settings.first()

        assertEquals(Settings.defaults(), settings)
    }

    @Test
    fun `update round trips every field away from its default`() = runBlocking {
        val nonDefault = Settings(true, 30, true, true)

        store.update { nonDefault }

        assertEquals(nonDefault, store.settings.first())
    }

    @Test
    fun `update transforms the previously stored value, not the defaults`() = runBlocking {
        store.update { current -> Settings(true, current.retentionDays(), current.pseudonymiseNumbers(), current.notifyOnBlock()) }
        store.update { current -> Settings(current.contactsBypassEnabled(), 7, current.pseudonymiseNumbers(), current.notifyOnBlock()) }

        val settings = store.settings.first()

        assertEquals(Settings(true, 7, false, false), settings)
    }
}
