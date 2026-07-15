package io.janda.vorwahlguard.data.rules

import io.janda.vorwahlguard.domain.model.PatternSyntax
import io.janda.vorwahlguard.domain.model.Rule
import io.janda.vorwahlguard.domain.model.RuleAction
import io.janda.vorwahlguard.domain.port.out.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists a newly created or edited [Rule], and removes one. The in-memory hot-path cache
 * ([CachedRuleRepository]) picks the change up asynchronously via
 * [RuleSnapshotSource.observe] — this class never touches it directly (CLAUDE.md §3 rule 1).
 */
@Singleton
class RuleWriter @Inject constructor(
    private val dao: RuleDao,
    private val clock: Clock,
) {

    suspend fun save(rule: Rule) {
        dao.upsert(rule.toEntity())
    }

    /**
     * The single "build a rule and persist it unless one for that pattern already exists" path,
     * shared by `AddRuleViewModel` and `ProtokollViewModel` (issue #78). Assigns a fresh id and
     * [Clock.now] timestamp, and delegates the existence check + insert to the atomic
     * [RuleDao.insertIfPatternAbsent] so duplicate creation is prevented at the database, not by
     * each caller racing its own observed snapshot. Returns [CreateRuleResult.CREATED] or
     * [CreateRuleResult.ALREADY_EXISTS].
     *
     * @throws IllegalArgumentException if [patternText] is not valid ([PatternSyntax]); callers
     *   pass only text they have already validated, so this signals a caller bug, not user input.
     */
    suspend fun createIfAbsent(patternText: String, action: RuleAction): CreateRuleResult {
        val rule = Rule(
            UUID.randomUUID().toString(),
            PatternSyntax.parse(patternText),
            action,
            true,
            null,
            clock.now(),
        )
        return if (dao.insertIfPatternAbsent(rule.toEntity())) {
            CreateRuleResult.CREATED
        } else {
            CreateRuleResult.ALREADY_EXISTS
        }
    }

    suspend fun delete(id: String) {
        dao.deleteById(id)
    }
}

/** Outcome of [RuleWriter.createIfAbsent] (issue #78). */
enum class CreateRuleResult { CREATED, ALREADY_EXISTS }
