package io.janda.vorwahlguard.data.rules

import io.janda.vorwahlguard.domain.model.Rule
import io.janda.vorwahlguard.domain.port.out.RuleRepository
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.collect

/**
 * In-memory rule cache (CLAUDE.md §3 rule 1). [activeRules] only reads the snapshot reference
 * — safe to call from the `onScreenCall()` hot path. [refresh] and [observeAndCache] do the
 * actual I/O via [RuleSnapshotSource] and must only ever be called off that path (service
 * `onCreate()`).
 *
 * Before the first [refresh], [activeRules] returns an empty list — [RuleMatcher] treats that
 * as "no match", which is the correct fail-safe default (allow, record nothing).
 */
@Singleton
class CachedRuleRepository @Inject constructor(
    private val ruleSnapshotSource: RuleSnapshotSource,
) : RuleRepository {

    private val snapshot = AtomicReference<List<Rule>>(emptyList())

    override fun activeRules(): List<Rule> = snapshot.get()

    /** The latched initial warm — only ever called off the `onScreenCall()` hot path. */
    fun refresh() {
        snapshot.set(ruleSnapshotSource.load())
    }

    /** Collects [RuleSnapshotSource.observe] forever — the caller's [kotlinx.coroutines.CoroutineScope] bounds it. */
    suspend fun observeAndCache() {
        ruleSnapshotSource.observe().collect { snapshot.set(it) }
    }
}
