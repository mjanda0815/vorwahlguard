package io.janda.vorwahlguard.data.events

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CallEventDao {

    @Insert
    suspend fun insert(event: CallEventEntity)

    @Query("DELETE FROM call_events WHERE occurred_at < :cutoffEpochMillis")
    suspend fun purgeOlderThan(cutoffEpochMillis: Long): Int

    @Query("SELECT * FROM call_events ORDER BY occurred_at DESC")
    fun observeNewestFirst(): Flow<List<CallEventEntity>>

    /** UI-only read (`UebersichtScreen`'s hero counter) — never called on the `onScreenCall()` hot path. */
    @Query("SELECT COUNT(*) FROM call_events")
    fun observeTotalCount(): Flow<Int>

    /** UI-only read (`UebersichtScreen`'s sparkline) — never called on the `onScreenCall()` hot path. */
    @Query("SELECT occurred_at FROM call_events WHERE occurred_at >= :sinceEpochMillis")
    fun observeOccurredAtSince(sinceEpochMillis: Long): Flow<List<Long>>

    /** UI-only read (`UebersichtScreen`'s top-countries list) — never called on the `onScreenCall()` hot path. */
    @Query(
        "SELECT region_code AS regionCode, COUNT(*) AS count FROM call_events " +
            "WHERE region_code != :unknownRegionCode GROUP BY region_code " +
            "ORDER BY count DESC, region_code ASC LIMIT 3",
    )
    fun observeTopRegions(unknownRegionCode: String = UNKNOWN_REGION_CODE): Flow<List<RegionCount>>

    /** UI-only read (`UebersichtScreen`'s top-rules list) — never called on the `onScreenCall()` hot path. */
    @Query(
        "SELECT matched_rule_id AS ruleId, COUNT(*) AS count FROM call_events " +
            "WHERE matched_rule_id IS NOT NULL " +
            "GROUP BY matched_rule_id ORDER BY count DESC, matched_rule_id ASC LIMIT 3",
    )
    fun observeTopRules(): Flow<List<RuleIdCount>>

    /**
     * UI-only read (`UebersichtScreen`'s action breakdown, issue #59) — never called on the
     * `onScreenCall()` hot path. One row per distinct `(action, reason)` pair actually present.
     */
    @Query("SELECT action, reason, COUNT(*) AS count FROM call_events GROUP BY action, reason")
    fun observeActionBreakdown(): Flow<List<ActionReasonCount>>
}
