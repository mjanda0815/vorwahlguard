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
}
