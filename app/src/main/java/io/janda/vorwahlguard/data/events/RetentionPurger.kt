package io.janda.vorwahlguard.data.events

import io.janda.vorwahlguard.data.settings.SettingsStore
import io.janda.vorwahlguard.domain.port.out.Clock
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Enforces the configurable [io.janda.vorwahlguard.domain.model.Settings.retentionDays] purge (CLAUDE.md §12). */
@Singleton
class RetentionPurger @Inject constructor(
    private val dao: CallEventDao,
    private val settingsStore: SettingsStore,
    private val clock: Clock,
) {

    suspend fun purge() {
        val retentionDays = settingsStore.settings.first().retentionDays()
        val cutoff = clock.now().minus(retentionDays.toLong(), ChronoUnit.DAYS)
        dao.purgeOlderThan(cutoff.toEpochMilli())
    }
}
