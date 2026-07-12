package io.janda.vorwahlguard.data.rules

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {

    /**
     * Blocking read — only ever called from the warm coroutine (CLAUDE.md §3 rule 1), never
     * from `onScreenCall()` itself.
     */
    @Query("SELECT * FROM rules WHERE enabled = 1")
    fun loadActive(): List<RuleEntity>

    @Query("SELECT * FROM rules WHERE enabled = 1")
    fun observeActive(): Flow<List<RuleEntity>>

    @Upsert
    suspend fun upsert(rule: RuleEntity)

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun deleteById(id: String)
}
