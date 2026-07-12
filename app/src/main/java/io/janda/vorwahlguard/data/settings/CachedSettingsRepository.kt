package io.janda.vorwahlguard.data.settings

import io.janda.vorwahlguard.domain.model.Settings
import io.janda.vorwahlguard.domain.port.out.SettingsRepository
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first

/**
 * In-memory settings cache (CLAUDE.md §3 rule 1). [current] only reads the snapshot reference —
 * safe to call from the `onScreenCall()` hot path. [refresh] and [observeAndCache] do the actual
 * I/O via [SettingsStore] and must only ever be called off that path (service `onCreate()`).
 *
 * Before the first [refresh], [current] returns [Settings.defaults] — the safest posture, never
 * an uninitialized/null state.
 */
@Singleton
class CachedSettingsRepository @Inject constructor(
    private val store: SettingsStore,
) : SettingsRepository {

    private val snapshot = AtomicReference(Settings.defaults())

    override fun current(): Settings = snapshot.get()

    suspend fun refresh() {
        snapshot.set(store.settings.first())
    }

    /** Collects [SettingsStore.settings] forever — the caller's [kotlinx.coroutines.CoroutineScope] bounds it. */
    suspend fun observeAndCache() {
        store.settings.collect { snapshot.set(it) }
    }
}
