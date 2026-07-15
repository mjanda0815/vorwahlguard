package io.janda.vorwahlguard.data.rules

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {

    @Query("SELECT EXISTS(SELECT 1 FROM rules WHERE pattern = :pattern)")
    suspend fun existsByPattern(pattern: String): Boolean

    /**
     * Inserts [rule] only if no rule with the same `pattern` text exists yet, atomically
     * (issue #78): check and insert run in one Room transaction, and Room serializes writers, so
     * two concurrent "create the same caller's rule" taps cannot both slip past the check.
     * Returns `true` if it inserted, `false` if a rule for that pattern already existed.
     */
    @Transaction
    suspend fun insertIfPatternAbsent(rule: RuleEntity): Boolean {
        if (existsByPattern(rule.pattern)) {
            return false
        }
        upsert(rule)
        return true
    }

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
