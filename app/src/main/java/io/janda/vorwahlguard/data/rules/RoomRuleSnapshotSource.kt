package io.janda.vorwahlguard.data.rules

import io.janda.vorwahlguard.domain.model.Rule
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed [RuleSnapshotSource]. A row whose stored pattern no longer parses (corrupt data)
 * is skipped rather than thrown for — a dropped rule degrades toward "no match" (allow), which
 * is the correct fail-safe default (CLAUDE.md §3 rule 1, §4 rule 3).
 */
class RoomRuleSnapshotSource @Inject constructor(
    private val dao: RuleDao,
) : RuleSnapshotSource {

    override fun load(): List<Rule> = dao.loadActive().mapNotNull { it.toDomain() }

    override fun observe(): Flow<List<Rule>> =
        dao.observeActive().map { rows -> rows.mapNotNull { it.toDomain() } }
}
