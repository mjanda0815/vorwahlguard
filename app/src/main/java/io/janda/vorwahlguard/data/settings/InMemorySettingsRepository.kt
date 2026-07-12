package io.janda.vorwahlguard.data.settings

import io.janda.vorwahlguard.domain.model.Settings
import io.janda.vorwahlguard.domain.port.out.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M2 stub [SettingsRepository]: fixed defaults, matching the safest posture (contacts bypass
 * off, no block notifications, 90-day retention, numbers not pseudonymised). M3 replaces the
 * `@Binds` target with a DataStore-backed implementation.
 */
@Singleton
class InMemorySettingsRepository @Inject constructor() : SettingsRepository {

    // core-domain is not compiled with -parameters, so named arguments are not available here;
    // order matches Settings' canonical constructor: contactsBypassEnabled, retentionDays,
    // pseudonymiseNumbers, notifyOnBlock.
    override fun current(): Settings = Settings(
        false,
        90,
        false,
        false,
    )
}
