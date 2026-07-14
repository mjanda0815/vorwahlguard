package io.janda.vorwahlguard.data.db

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.janda.vorwahlguard.data.SystemClock
import io.janda.vorwahlguard.data.events.CallEventDao
import io.janda.vorwahlguard.data.events.CallEventEntity
import io.janda.vorwahlguard.data.events.RetentionPurger
import io.janda.vorwahlguard.data.rules.RuleDao
import io.janda.vorwahlguard.data.rules.toEntity
import io.janda.vorwahlguard.data.settings.SettingsStore
import io.janda.vorwahlguard.domain.model.DecisionReason
import io.janda.vorwahlguard.domain.model.PatternSyntax
import io.janda.vorwahlguard.domain.model.Rule
import io.janda.vorwahlguard.domain.model.RuleAction
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises [RuleDao] and [RetentionPurger] against a real, on-device SQLite database — Room's
 * generated SQL, not Robolectric's shadow implementation. Catches exactly the class of bug a
 * mocked-DAO unit test cannot: a wrong `WHERE` clause in a real query (store-readiness review,
 * issue #29).
 */
@RunWith(AndroidJUnit4::class)
class RoomInstrumentedTest {

    private lateinit var database: VorwahlGuardDatabase
    private lateinit var ruleDao: RuleDao
    private lateinit var callEventDao: CallEventDao
    private lateinit var settingsDataStoreFileName: String

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, VorwahlGuardDatabase::class.java).build()
        ruleDao = database.ruleDao()
        callEventDao = database.callEventDao()
        settingsDataStoreFileName = "room_instrumented_test_settings_${UUID.randomUUID()}"
    }

    @After
    fun tearDown() {
        database.close()
        InstrumentationRegistry.getInstrumentation().targetContext
            .preferencesDataStoreFile(settingsDataStoreFileName)
            .delete()
    }

    @Test
    fun upsertedRuleIsEmittedByObserveActive() = runTest {
        val rule = rule(pattern = "+43*", action = RuleAction.SILENCE)

        ruleDao.upsert(rule.toEntity())

        val active = ruleDao.observeActive().first()
        assertEquals(1, active.size)
        assertEquals(rule.id(), active.first().id)
    }

    @Test
    fun deleteByIdRemovesExactlyThatRule() = runTest {
        val keep = rule(pattern = "+43*", action = RuleAction.SILENCE)
        val remove = rule(pattern = "+49*", action = RuleAction.BLOCK)
        ruleDao.upsert(keep.toEntity())
        ruleDao.upsert(remove.toEntity())

        ruleDao.deleteById(remove.id())

        val active = ruleDao.observeActive().first()
        assertEquals(listOf(keep.id()), active.map { it.id })
    }

    @Test
    fun retentionPurgeDeletesOnlyEventsOlderThanTheConfiguredWindow() = runTest {
        val settingsStore = SettingsStore(
            PreferenceDataStoreFactory.create(
                produceFile = {
                    InstrumentationRegistry.getInstrumentation().targetContext
                        .preferencesDataStoreFile(settingsDataStoreFileName)
                },
            ),
        )
        val clock = SystemClock()
        val now = clock.now()
        // A fresh, empty DataStore falls back to Settings.defaults() (retentionDays = 90) —
        // see SettingsStore.toSettings() — so no explicit settingsStore.update() is needed.
        val retentionDays = 90L

        val old = event(id = "old", occurredAt = now.minus(retentionDays + 1, ChronoUnit.DAYS))
        val recent = event(id = "recent", occurredAt = now.minus(1, ChronoUnit.DAYS))
        callEventDao.insert(old)
        callEventDao.insert(recent)

        RetentionPurger(callEventDao, settingsStore, clock).purge()

        val remaining = callEventDao.observeNewestFirst().first()
        assertTrue(remaining.none { it.id == "old" })
        assertTrue(remaining.any { it.id == "recent" })
    }

    private fun rule(pattern: String, action: RuleAction): Rule = Rule(
        UUID.randomUUID().toString(),
        PatternSyntax.parse(pattern),
        action,
        true,
        null,
        Instant.now(),
    )

    private fun event(id: String, occurredAt: Instant): CallEventEntity = CallEventEntity(
        id = id,
        occurredAt = occurredAt,
        numberOrHash = "+436631234567",
        isHashed = false,
        regionCode = "AT",
        matchedRuleId = "some-rule",
        action = RuleAction.BLOCK.name,
        reason = DecisionReason.RULE_MATCH.name,
    )
}
