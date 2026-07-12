package io.janda.vorwahlguard.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.janda.vorwahlguard.domain.port.`in`.ScreenIncomingCall
import io.janda.vorwahlguard.domain.port.out.NumberNormalizer
import io.janda.vorwahlguard.domain.port.out.RuleRepository
import io.janda.vorwahlguard.domain.service.LibPhoneNumberNormalizer
import io.janda.vorwahlguard.domain.service.ScreenIncomingCallService
import javax.inject.Singleton

/**
 * Wires the plain-Java `:core-domain` services into Hilt. Neither [ScreenIncomingCallService]
 * nor [LibPhoneNumberNormalizer] carries any Dagger annotation — the domain never sees Hilt
 * (docs/ARCHITECTURE.md).
 */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    @Provides
    @Singleton
    fun provideScreenIncomingCall(ruleRepository: RuleRepository): ScreenIncomingCall =
        ScreenIncomingCallService(ruleRepository)

    @Provides
    @Singleton
    fun provideNumberNormalizer(): NumberNormalizer = LibPhoneNumberNormalizer()
}
