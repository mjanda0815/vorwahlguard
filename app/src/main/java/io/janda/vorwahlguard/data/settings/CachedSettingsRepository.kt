package io.janda.vorwahlguard.data.settings

import io.janda.vorwahlguard.domain.model.Settings
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
 * Injected as the concrete type ([io.janda.vorwahlguard.screening.VorwahlGuardScreeningService]):
 * unlike rules, the domain's `decide()` takes no settings, so there is no driven port for this to
 * implement — an interface here would be scaffolding with no second implementation and no domain
 * consumer.
 *
 * Before the first [refresh], [current] returns [Settings.defaults] — the safest posture, never
 * an uninitialized/null state.
 */
@Singleton
class CachedSettingsRepository @Inject constructor(
    private val store: SettingsStore,
) {

    private val snapshot = AtomicReference(Settings.defaults())

    fun current(): Settings = snapshot.get()

    suspend fun refresh() {
        snapshot.set(store.settings.first())
    }

    /** Collects [SettingsStore.settings] forever — the caller's [kotlinx.coroutines.CoroutineScope] bounds it. */
    suspend fun observeAndCache() {
        store.settings.collect { snapshot.set(it) }
    }
}
