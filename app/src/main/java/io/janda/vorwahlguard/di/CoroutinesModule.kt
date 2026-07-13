package io.janda.vorwahlguard.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Marks the process-wide [CoroutineScope] used by adapters that must outlive a single caller —
 * e.g. [io.janda.vorwahlguard.data.events.RoomCallEventRecorder], which is invoked from the
 * `onScreenCall()` hot path but must not do I/O on the calling thread (CLAUDE.md §3 rule 1).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * Marks [Dispatchers.Default] for CPU-bound, non-blocking work off the main thread — e.g.
 * [io.janda.vorwahlguard.ui.regeln.addrule.AddRuleViewModel] building the sorted country list
 * or filtering it, which is neither UI work nor disk/network I/O.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/**
 * Marks [Dispatchers.IO] for blocking I/O triggered from UI code — e.g.
 * [io.janda.vorwahlguard.ui.einstellungen.EinstellungenViewModel] running a settings-screen-
 * initiated [io.janda.vorwahlguard.data.contacts.CachedContactsLookup.refresh] `ContentResolver`
 * query. Distinct from [DefaultDispatcher]'s CPU-bound, non-blocking intent: this one is for work
 * that blocks the thread on disk/content-provider access, not for computation.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
