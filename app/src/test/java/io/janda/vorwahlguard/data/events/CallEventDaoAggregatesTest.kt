package io.janda.vorwahlguard.data.events

import androidx.room.Room
import io.janda.vorwahlguard.data.db.VorwahlGuardDatabase
import io.janda.vorwahlguard.domain.model.DecisionReason
import io.janda.vorwahlguard.domain.model.RuleAction
import java.time.Instant
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
 * Covers the four issue #27 aggregate queries on [CallEventDao] against a real in-memory Room
 * database (Robolectric, mirroring [io.janda.vorwahlguard.data.rules.RoomRuleSnapshotSourceTest]'s
 * setup): [CallEventDao.observeTotalCount]'s live update, [CallEventDao.observeOccurredAtSince]'s
 * exact `>=` cutoff semantics, and the grouping/ordering/`LIMIT 3` contract of
 * [CallEventDao.observeTopRegions] (including the [UNKNOWN_REGION_CODE] exclusion) and
 * [CallEventDao.observeTopRules].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class CallEventDaoAggregatesTest {

    private lateinit var database: VorwahlGuardDatabase
    private lateinit var dao: CallEventDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            VorwahlGuardDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.callEventDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun entity(
        id: String,
        occurredAt: Instant = Instant.EPOCH,
        numberOrHash: String = "+4915112345678",
        isHashed: Boolean = false,
        regionCode: String = "AT",
        matchedRuleId: String? = "rule-1",
        action: String = RuleAction.BLOCK.name,
        reason: String = DecisionReason.RULE_MATCH.name,
    ): CallEventEntity =
        CallEventEntity(id, occurredAt, numberOrHash, isHashed, regionCode, matchedRuleId, action, reason)

    @Test
    fun `observeTotalCount reflects the current row count`() = runBlocking {
        dao.insert(entity("event-1"))
        dao.insert(entity("event-2"))

        assertEquals(2, dao.observeTotalCount().first())
    }

    @Test
    fun `observeTotalCount updates when a new row is inserted`() = runBlocking {
        dao.insert(entity("event-1"))
        assertEquals(1, dao.observeTotalCount().first())

        dao.insert(entity("event-2"))

        assertEquals(2, dao.observeTotalCount().first())
    }

    @Test
    fun `observeOccurredAtSince includes a row exactly at the cutoff`() = runBlocking {
        val cutoff = Instant.parse("2026-07-13T10:00:00Z")
        dao.insert(entity("at-cutoff", occurredAt = cutoff))

        val result = dao.observeOccurredAtSince(cutoff.toEpochMilli()).first()

        assertEquals(listOf(cutoff.toEpochMilli()), result)
    }

    @Test
    fun `observeOccurredAtSince excludes a row strictly before the cutoff`() = runBlocking {
        val cutoff = Instant.parse("2026-07-13T10:00:00Z")
        dao.insert(entity("before-cutoff", occurredAt = cutoff.minusMillis(1)))

        val result = dao.observeOccurredAtSince(cutoff.toEpochMilli()).first()

        assertEquals(emptyList<Long>(), result)
    }

    @Test
    fun `observeOccurredAtSince includes a row after the cutoff`() = runBlocking {
        val cutoff = Instant.parse("2026-07-13T10:00:00Z")
        dao.insert(entity("after-cutoff", occurredAt = cutoff.plusMillis(1)))

        val result = dao.observeOccurredAtSince(cutoff.toEpochMilli()).first()

        assertEquals(listOf(cutoff.plusMillis(1).toEpochMilli()), result)
    }

    @Test
    fun `observeTopRegions excludes rows whose region code is the unknown sentinel`() = runBlocking {
        dao.insert(entity("known", regionCode = "AT"))
        dao.insert(entity("unknown", regionCode = UNKNOWN_REGION_CODE))

        val result = dao.observeTopRegions().first()

        assertEquals(listOf(RegionCount("AT", 1)), result)
    }

    @Test
    fun `observeTopRegions groups rows by region code and counts them`() = runBlocking {
        dao.insert(entity("at-1", regionCode = "AT"))
        dao.insert(entity("at-2", regionCode = "AT"))
        dao.insert(entity("de-1", regionCode = "DE"))

        val result = dao.observeTopRegions().first()

        assertEquals(listOf(RegionCount("AT", 2), RegionCount("DE", 1)), result)
    }

    @Test
    fun `observeTopRegions orders by count descending then region code ascending on a tie`() = runBlocking {
        // DE and CH tie at one row each: alphabetical region_code ASC must break the tie, not
        // insertion order (CH is inserted first here, so a wrong ORDER BY would put CH first).
        dao.insert(entity("ch-1", regionCode = "CH"))
        dao.insert(entity("de-1", regionCode = "DE"))
        dao.insert(entity("at-1", regionCode = "AT"))
        dao.insert(entity("at-2", regionCode = "AT"))

        val result = dao.observeTopRegions().first()

        assertEquals(
            listOf(RegionCount("AT", 2), RegionCount("CH", 1), RegionCount("DE", 1)),
            result,
        )
    }

    @Test
    fun `observeTopRegions respects the LIMIT 3 across more than three distinct regions`() = runBlocking {
        // Five distinct regions, counts AT=2, DE=2, CH=1, FR=1, IT=1: the count-desc/region-asc
        // ordering puts AT, DE (tied at 2, alphabetical) ahead of the three regions tied at 1, so
        // the top three are AT, DE, CH — FR and IT must be cut off by LIMIT 3.
        dao.insert(entity("at-1", regionCode = "AT"))
        dao.insert(entity("at-2", regionCode = "AT"))
        dao.insert(entity("de-1", regionCode = "DE"))
        dao.insert(entity("de-2", regionCode = "DE"))
        dao.insert(entity("ch-1", regionCode = "CH"))
        dao.insert(entity("fr-1", regionCode = "FR"))
        dao.insert(entity("it-1", regionCode = "IT"))

        val result = dao.observeTopRegions().first()

        assertEquals(
            listOf(RegionCount("AT", 2), RegionCount("DE", 2), RegionCount("CH", 1)),
            result,
        )
    }

    @Test
    fun `observeTopRules groups rows by matched rule id and counts them`() = runBlocking {
        dao.insert(entity("rule-a-1", matchedRuleId = "rule-a"))
        dao.insert(entity("rule-a-2", matchedRuleId = "rule-a"))
        dao.insert(entity("rule-b-1", matchedRuleId = "rule-b"))

        val result = dao.observeTopRules().first()

        assertEquals(listOf(RuleIdCount("rule-a", 2), RuleIdCount("rule-b", 1)), result)
    }

    @Test
    fun `observeTopRules orders by count descending then rule id ascending on a tie`() = runBlocking {
        dao.insert(entity("rule-c-1", matchedRuleId = "rule-c"))
        dao.insert(entity("rule-b-1", matchedRuleId = "rule-b"))
        dao.insert(entity("rule-a-1", matchedRuleId = "rule-a"))
        dao.insert(entity("rule-a-2", matchedRuleId = "rule-a"))

        val result = dao.observeTopRules().first()

        assertEquals(
            listOf(RuleIdCount("rule-a", 2), RuleIdCount("rule-b", 1), RuleIdCount("rule-c", 1)),
            result,
        )
    }

    @Test
    fun `observeTopRules respects the LIMIT 3 across more than three distinct rule ids`() = runBlocking {
        dao.insert(entity("rule-a-1", matchedRuleId = "rule-a"))
        dao.insert(entity("rule-a-2", matchedRuleId = "rule-a"))
        dao.insert(entity("rule-b-1", matchedRuleId = "rule-b"))
        dao.insert(entity("rule-c-1", matchedRuleId = "rule-c"))
        dao.insert(entity("rule-d-1", matchedRuleId = "rule-d"))

        val result = dao.observeTopRules().first()

        assertEquals(3, result.size)
        assertEquals(RuleIdCount("rule-a", 2), result.first())
    }

    @Test
    fun `a reason-only row is counted in observeTotalCount and observeNewestFirst but excluded from observeTopRules`() = runBlocking {
        dao.insert(
            entity(
                "reason-only",
                matchedRuleId = null,
                action = RuleAction.ALLOW.name,
                reason = DecisionReason.NO_MATCH.name,
            ),
        )
        dao.insert(entity("rule-matched", matchedRuleId = "rule-a"))

        assertEquals(2, dao.observeTotalCount().first())
        assertEquals(setOf("reason-only", "rule-matched"), dao.observeNewestFirst().first().map { it.id }.toSet())
        assertEquals(listOf(RuleIdCount("rule-a", 1)), dao.observeTopRules().first())
    }

    @Test
    fun `observeActionBreakdown groups by action and reason`() = runBlocking {
        dao.insert(entity("block-1", action = RuleAction.BLOCK.name, matchedRuleId = "rule-a"))
        dao.insert(entity("block-2", action = RuleAction.BLOCK.name, matchedRuleId = "rule-a"))
        dao.insert(
            entity(
                "contact-1",
                matchedRuleId = null,
                action = RuleAction.ALLOW.name,
                reason = DecisionReason.CONTACT_BYPASS.name,
            ),
        )
        dao.insert(
            entity(
                "no-match-1",
                matchedRuleId = null,
                action = RuleAction.ALLOW.name,
                reason = DecisionReason.NO_MATCH.name,
            ),
        )

        val result = dao.observeActionBreakdown().first().toSet()

        assertEquals(
            setOf(
                ActionReasonCount(RuleAction.BLOCK.name, DecisionReason.RULE_MATCH.name, 2),
                ActionReasonCount(RuleAction.ALLOW.name, DecisionReason.CONTACT_BYPASS.name, 1),
                ActionReasonCount(RuleAction.ALLOW.name, DecisionReason.NO_MATCH.name, 1),
            ),
            result,
        )
    }
}
