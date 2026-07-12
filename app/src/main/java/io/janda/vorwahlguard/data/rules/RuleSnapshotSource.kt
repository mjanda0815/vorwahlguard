package io.janda.vorwahlguard.data.rules

import io.janda.vorwahlguard.domain.model.Rule

/**
 * The source of truth [CachedRuleRepository] snapshots into memory. M2 ships
 * [StaticRuleSnapshotSource], a hardcoded stub; M3 replaces the `@Binds` target with a
 * Room-backed implementation without touching [CachedRuleRepository] or anything downstream
 * of it (docs/ARCHITECTURE.md).
 */
interface RuleSnapshotSource {

    fun load(): List<Rule>
}
