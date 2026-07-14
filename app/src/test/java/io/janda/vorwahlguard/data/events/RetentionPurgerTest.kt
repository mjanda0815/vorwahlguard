package io.janda.vorwahlguard.data.events

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import io.janda.vorwahlguard.data.db.VorwahlGuardDatabase
import io.janda.vorwahlguard.data.settings.SettingsStore
import io.janda.vorwahlguard.domain.model.Settings
import io.janda.vorwahlguard.domain.port.out.Clock
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [RetentionPurger] against a real in-memory Room [CallEventDao] (Robolectric, since Room needs
 * an Android [android.content.Context]), a real [SettingsStore] on a temp DataStore, and a
 * mocked [Clock] pinned to a fixed instant (CLAUDE.md §12: the configurable retention purge).
 * [CallEventDao.purgeOlderThan] is a strict `<` — an event exactly at the cutoff must survive.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class RetentionPurgerTest {

    private lateinit var database: VorwahlGuardDatabase
    private lateinit var dao: CallEventDao
    private lateinit var clock: Clock
    private val tempFiles = mutableListOf<File>()
    private val now = Instant.parse("2026-07-12T00:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            VorwahlGuardDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.callEventDao()
        clock = mockk()
        every { clock.now() } returns now
    }

    @After
    fun tearDown() {
        database.close()
        tempFiles.forEach { it.delete() }
    }

    @Test
    fun `default 90 day retention purges only events strictly older than the cutoff`() = runBlocking {
        val settingsStore = settingsStoreWith(Settings.defaults())
        val cutoff = now.minus(90, ChronoUnit.DAYS)
        dao.insert(eventAt(id = "just-before-cutoff", occurredAt = cutoff.minusMillis(1)))
        dao.insert(eventAt(id = "exactly-at-cutoff", occurredAt = cutoff))
        dao.insert(eventAt(id = "just-after-cutoff", occurredAt = cutoff.plusMillis(1)))

        RetentionPurger(dao, settingsStore, clock).purge()

        assertEquals(setOf("exactly-at-cutoff", "just-after-cutoff"), remainingIds())
    }

    @Test
    fun `a non-default retention_days setting is honored`() = runBlocking {
        val settingsStore = settingsStoreWith(Settings(false, 30, false, false, false))
        val cutoff = now.minus(30, ChronoUnit.DAYS)
        dao.insert(eventAt(id = "exactly-at-cutoff", occurredAt = cutoff))
        // 45 days old: would have survived the default 90-day retention, but not this 30-day one.
        dao.insert(eventAt(id = "45-days-old", occurredAt = now.minus(45, ChronoUnit.DAYS)))

        RetentionPurger(dao, settingsStore, clock).purge()

        assertEquals(setOf("exactly-at-cutoff"), remainingIds())
    }

    private fun remainingIds(): Set<String> = runBlocking { dao.observeNewestFirst().first() }.map { it.id }.toSet()

    private fun settingsStoreWith(settings: Settings): SettingsStore {
        val file = File.createTempFile("retention-purger-test", ".preferences_pb")
        file.delete()
        tempFiles.add(file)
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        val store = SettingsStore(dataStore)
        runBlocking { store.update { settings } }
        return store
    }

    private fun eventAt(id: String, occurredAt: Instant): CallEventEntity = CallEventEntity(
        id = id,
        occurredAt = occurredAt,
        numberOrHash = "+431234567",
        isHashed = false,
        regionCode = "AT",
        matchedRuleId = "rule-1",
        action = "BLOCK",
        reason = "RULE_MATCH",
    )
}
