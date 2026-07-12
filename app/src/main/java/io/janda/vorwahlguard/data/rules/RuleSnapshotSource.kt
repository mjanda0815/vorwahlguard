package io.janda.vorwahlguard.data.rules

import io.janda.vorwahlguard.domain.model.Rule
import kotlinx.coroutines.flow.Flow

/**
 * The source of truth [CachedRuleRepository] snapshots into memory. M3 ships
 * [RoomRuleSnapshotSource], backed by [RuleDao]: [load] serves the initial warm (CLAUDE.md §3
 * rule 1 — only ever called off the `onScreenCall()` hot path), and [observe] lets
 * [CachedRuleRepository.observeAndCache] keep the snapshot current after that without touching
 * disk on the calling thread again.
 */
interface RuleSnapshotSource {

    fun load(): List<Rule>

    fun observe(): Flow<List<Rule>>
}
