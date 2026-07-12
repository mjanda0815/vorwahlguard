package io.janda.vorwahlguard.data.events

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import io.janda.vorwahlguard.data.db.VorwahlGuardDatabase
import io.janda.vorwahlguard.data.settings.SettingsStore
import io.janda.vorwahlguard.domain.model.CallEvent
import io.janda.vorwahlguard.domain.model.RuleAction
import io.janda.vorwahlguard.domain.model.Settings
import io.mockk.coEvery
import io.mockk.mockk
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [RoomCallEventRecorder] against a real in-memory Room [CallEventDao] and a real
 * [PseudonymSaltProvider]/[NumberPseudonymiser] (Robolectric, since Room needs an Android
 * [android.content.Context]). [record] itself does zero I/O on the calling thread (CLAUDE.md §3
 * rule 1) — it only launches on the injected scope, so every test awaits the launched child job
 * rather than asserting immediately after the call returns.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class RoomCallEventRecorderTest {

    private lateinit var database: VorwahlGuardDatabase
    private lateinit var dao: CallEventDao
    private lateinit var saltFile: File
    private lateinit var saltDataStore: DataStore<Preferences>
    private lateinit var saltProvider: PseudonymSaltProvider
    private val tempFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            VorwahlGuardDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.callEventDao()

        saltFile = newTempFile("salt")
        saltDataStore = PreferenceDataStoreFactory.create(produceFile = { saltFile })
        saltProvider = PseudonymSaltProvider(saltDataStore)
    }

    @After
    fun tearDown() {
        database.close()
        tempFiles.forEach { it.delete() }
    }

    @Test
    fun `pseudonymisation off stores the raw number with isHashed false`() {
        val (recorder, job, _) = recorderWith(Settings(false, 90, false, false), dao)
        val event = event(numberOrHash = "+431234567")

        recorder.record(event)
        awaitCompletion(job)

        val stored = singleStoredEvent()
        assertEquals("+431234567", stored.numberOrHash)
        assertFalse(stored.isHashed)
    }

    @Test
    fun `pseudonymisation on stores the sha256 hash with isHashed true and plain metadata`() {
        val (recorder, job, _) = recorderWith(Settings(false, 90, true, false), dao)
        val event = event(numberOrHash = "+431234567", regionCode = "AT", matchedRuleId = "rule-1", action = RuleAction.BLOCK)

        recorder.record(event)
        awaitCompletion(job)

        val expectedHash = runBlocking { NumberPseudonymiser.hash("+431234567", saltProvider.salt()) }
        val stored = singleStoredEvent()
        assertEquals(expectedHash, stored.numberOrHash)
        assertTrue(stored.isHashed)
        assertEquals("AT", stored.regionCode)
        assertEquals("rule-1", stored.matchedRuleId)
        assertEquals("BLOCK", stored.action)
    }

    @Test
    fun `a PRIVATE placeholder is stored raw even when pseudonymisation is on`() {
        val (recorder, job, _) = recorderWith(Settings(false, 90, true, false), dao)
        val event = event(numberOrHash = "PRIVATE")

        recorder.record(event)
        awaitCompletion(job)

        val stored = singleStoredEvent()
        assertEquals("PRIVATE", stored.numberOrHash)
        assertFalse(stored.isHashed)
    }

    @Test
    fun `a throwing DAO does not propagate out of record`() {
        val throwingDao = mockk<CallEventDao>()
        coEvery { throwingDao.insert(any()) } throws RuntimeException("disk full")
        val (recorder, job, uncaught) = recorderWith(Settings(false, 90, false, false), throwingDao)

        // record() must not throw, and the launched coroutine must not fail either. The scope's
        // CoroutineExceptionHandler captures anything that escapes record()'s internal
        // runCatching — in production that escape would crash the process.
        recorder.record(event(numberOrHash = "+431234567"))
        awaitCompletion(job)

        assertTrue("expected no uncaught exception, got $uncaught", uncaught.isEmpty())
    }

    private fun recorderWith(
        settings: Settings,
        callEventDao: CallEventDao,
    ): Triple<RoomCallEventRecorder, Job, List<Throwable>> {
        val settingsFile = newTempFile("settings")
        val settingsDataStore = PreferenceDataStoreFactory.create(produceFile = { settingsFile })
        val settingsStore = SettingsStore(settingsDataStore)
        runBlocking { settingsStore.update { settings } }

        val job = SupervisorJob()
        val uncaught = mutableListOf<Throwable>()
        val handler = CoroutineExceptionHandler { _, t -> uncaught.add(t) }
        val scope = CoroutineScope(Dispatchers.Unconfined + job + handler)
        val recorder = RoomCallEventRecorder(callEventDao, settingsStore, saltProvider, scope)
        return Triple(recorder, job, uncaught)
    }

    /** [record] only launches work; wait for that launched child to actually finish. */
    private fun awaitCompletion(job: Job) = runBlocking {
        job.children.forEach { it.join() }
    }

    private fun singleStoredEvent(): CallEventEntity = runBlocking { dao.observeNewestFirst().first() }.single()

    private fun event(
        numberOrHash: String,
        regionCode: String = "AT",
        matchedRuleId: String = "rule-1",
        action: RuleAction = RuleAction.BLOCK,
    ): CallEvent = CallEvent("event-1", Instant.EPOCH, numberOrHash, regionCode, matchedRuleId, action)

    private fun newTempFile(prefix: String): File {
        val file = File.createTempFile("$prefix-recorder-test", ".preferences_pb")
        file.delete()
        tempFiles.add(file)
        return file
    }
}
