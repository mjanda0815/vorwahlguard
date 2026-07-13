package io.janda.vorwahlguard.data.rules

import io.janda.vorwahlguard.domain.model.Rule
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
) {

    suspend fun save(rule: Rule) {
        dao.upsert(rule.toEntity())
    }

    suspend fun delete(id: String) {
        dao.deleteById(id)
    }
}
