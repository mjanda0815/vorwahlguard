package io.janda.vorwahlguard.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.janda.vorwahlguard.data.SystemClock
import io.janda.vorwahlguard.data.contacts.CachedContactsLookup
import io.janda.vorwahlguard.data.events.NoOpCallEventRecorder
import io.janda.vorwahlguard.data.rules.CachedRuleRepository
import io.janda.vorwahlguard.data.rules.RuleSnapshotSource
import io.janda.vorwahlguard.data.rules.StaticRuleSnapshotSource
import io.janda.vorwahlguard.data.settings.InMemorySettingsRepository
import io.janda.vorwahlguard.domain.port.out.CallEventRecorder
import io.janda.vorwahlguard.domain.port.out.Clock
import io.janda.vorwahlguard.domain.port.out.ContactsLookup
import io.janda.vorwahlguard.domain.port.out.RuleRepository
import io.janda.vorwahlguard.domain.port.out.SettingsRepository
import javax.inject.Singleton

/**
 * Binds the `:app` adapters to the `:core-domain` driven ports (docs/ARCHITECTURE.md). Every
 * binding here is an M2 in-memory stub except [CachedRuleRepository] and [CachedContactsLookup]
 * (the caches themselves are the real M2 deliverable); M3 replaces
 * [StaticRuleSnapshotSource]/[InMemorySettingsRepository]/[NoOpCallEventRecorder] with
 * Room/DataStore-backed implementations without touching anything downstream.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AdapterModule {

    @Binds
    @Singleton
    abstract fun bindRuleRepository(impl: CachedRuleRepository): RuleRepository

    @Binds
    abstract fun bindRuleSnapshotSource(impl: StaticRuleSnapshotSource): RuleSnapshotSource

    @Binds
    @Singleton
    abstract fun bindContactsLookup(impl: CachedContactsLookup): ContactsLookup

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: InMemorySettingsRepository): SettingsRepository

    @Binds
    abstract fun bindCallEventRecorder(impl: NoOpCallEventRecorder): CallEventRecorder

    @Binds
    @Singleton
    abstract fun bindClock(impl: SystemClock): Clock
}
