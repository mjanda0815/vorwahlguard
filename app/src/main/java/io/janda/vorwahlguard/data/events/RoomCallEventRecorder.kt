package io.janda.vorwahlguard.data.events

import io.janda.vorwahlguard.data.settings.SettingsStore
import io.janda.vorwahlguard.di.ApplicationScope
import io.janda.vorwahlguard.domain.model.CallEvent
import io.janda.vorwahlguard.domain.port.out.CallEventRecorder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Persists a [CallEvent] to Room. [record] itself does zero I/O on the calling thread (CLAUDE.md
 * §3 rule 1): it only launches work on [applicationScope], which outlives the screening service
 * that invoked it. A failed insert must never propagate — the call has already been answered by
 * the time this runs, so the only thing a thrown exception could do here is crash the process.
 */
@Singleton
class RoomCallEventRecorder @Inject constructor(
    private val dao: CallEventDao,
    private val settingsStore: SettingsStore,
    private val saltProvider: PseudonymSaltProvider,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : CallEventRecorder {

    override fun record(event: CallEvent) {
        applicationScope.launch {
            runCatching {
                val settings = settingsStore.settings.first()
                val raw = event.numberOrHash()
                // Placeholders (e.g. "PRIVATE") never start with '+' and are stored as-is —
                // there is no number to pseudonymise. Never log [raw] (CLAUDE.md §1, §12).
                val isE164 = raw.startsWith("+")
                val storeHashed = settings.pseudonymiseNumbers() && isE164
                val numberOrHash = if (storeHashed) {
                    NumberPseudonymiser.hash(raw, saltProvider.salt())
                } else {
                    raw
                }

                dao.insert(
                    CallEventEntity(
                        id = event.id(),
                        occurredAt = event.occurredAt(),
                        numberOrHash = numberOrHash,
                        isHashed = storeHashed,
                        regionCode = event.regionCode(),
                        matchedRuleId = event.matchedRuleId(),
                        action = event.action().name,
                        reason = event.reason().name,
                    ),
                )
            }
        }
    }
}
