package io.janda.vorwahlguard.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
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

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
