package io.janda.vorwahlguard.data.rules

import androidx.room.Room
import io.janda.vorwahlguard.data.db.VorwahlGuardDatabase
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [RoomRuleSnapshotSource] against a real in-memory Room database (Robolectric, since Room needs
 * an Android [android.content.Context]). Covers the "corrupt row degrades to no-match, never
 * throws" contract from CLAUDE.md §3 rule 1 / §4 rule 3.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class RoomRuleSnapshotSourceTest {

    private lateinit var database: VorwahlGuardDatabase
    private lateinit var dao: RuleDao
    private lateinit var source: RoomRuleSnapshotSource

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            VorwahlGuardDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.ruleDao()
        source = RoomRuleSnapshotSource(dao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `load returns only enabled rules mapped to domain`() = runBlocking {
        dao.upsert(ruleEntity(id = "enabled", enabled = true))
        dao.upsert(ruleEntity(id = "disabled", enabled = false))

        val rules = source.load()

        assertEquals(listOf("enabled"), rules.map { it.id() })
    }

    @Test
    fun `a row with corrupt pattern text is skipped rather than thrown for`() = runBlocking {
        dao.upsert(ruleEntity(id = "corrupt", pattern = "+43**", enabled = true))
        dao.upsert(ruleEntity(id = "valid", pattern = "+43*", enabled = true))

        val rules = source.load()

        assertEquals(listOf("valid"), rules.map { it.id() })
    }

    @Test
    fun `observe emits again after an upsert`() = runBlocking {
        val emissions = mutableListOf<List<String>>()
        val job = launch {
            source.observe().collect { rules -> emissions.add(rules.map { it.id() }) }
        }

        awaitEmissionCount(emissions, 1)
        assertTrue(emissions.single().isEmpty())

        dao.upsert(ruleEntity(id = "new-rule", enabled = true))

        awaitEmissionCount(emissions, 2)
        assertEquals(listOf("new-rule"), emissions.last())

        job.cancel()
    }

    /** [delay] rather than a busy-spinning [kotlinx.coroutines.yield] so this genuinely parks. */
    private suspend fun awaitEmissionCount(emissions: List<*>, count: Int) {
        withTimeout(10_000) {
            while (emissions.size < count) {
                delay(10)
            }
        }
    }

    private fun ruleEntity(
        id: String,
        pattern: String = "+43*",
        action: String = "BLOCK",
        enabled: Boolean,
    ): RuleEntity = RuleEntity(
        id = id,
        pattern = pattern,
        action = action,
        enabled = enabled,
        label = null,
        createdAt = Instant.EPOCH,
    )
}
