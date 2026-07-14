package io.janda.vorwahlguard.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.janda.vorwahlguard.data.SystemClock
import io.janda.vorwahlguard.data.contacts.CachedContactsLookup
import io.janda.vorwahlguard.data.events.RoomCallEventRecorder
import io.janda.vorwahlguard.data.rules.CachedRuleRepository
import io.janda.vorwahlguard.data.rules.RoomRuleSnapshotSource
import io.janda.vorwahlguard.data.rules.RuleSnapshotSource
import io.janda.vorwahlguard.domain.port.out.CallEventRecorder
import io.janda.vorwahlguard.domain.port.out.Clock
import io.janda.vorwahlguard.domain.port.out.ContactsLookup
import io.janda.vorwahlguard.domain.port.out.RuleRepository
import javax.inject.Singleton

/**
 * Binds the `:app` adapters to the `:core-domain` driven ports (docs/ARCHITECTURE.md). M3
 * replaces every M2 in-memory stub with a Room/DataStore-backed implementation: rules and
 * settings are still served from an in-memory cache for the `onScreenCall()` hot path
 * (CLAUDE.md §3 rule 1), but that cache is now warmed from — and kept current by observing —
 * persistent storage instead of a hardcoded value. No stub bindings remain.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AdapterModule {

    @Binds
    @Singleton
    abstract fun bindRuleRepository(impl: CachedRuleRepository): RuleRepository

    @Binds
    abstract fun bindRuleSnapshotSource(impl: RoomRuleSnapshotSource): RuleSnapshotSource

    @Binds
    @Singleton
    abstract fun bindContactsLookup(impl: CachedContactsLookup): ContactsLookup

    // No settings binding: CachedSettingsRepository is injected as the concrete type — the domain
    // has no settings port for it to implement (see that class's KDoc).

    @Binds
    @Singleton
    abstract fun bindCallEventRecorder(impl: RoomCallEventRecorder): CallEventRecorder

    @Binds
    @Singleton
    abstract fun bindClock(impl: SystemClock): Clock
}
