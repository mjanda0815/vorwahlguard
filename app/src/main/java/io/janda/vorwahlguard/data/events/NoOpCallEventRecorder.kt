package io.janda.vorwahlguard.data.events

import io.janda.vorwahlguard.domain.model.CallEvent
import io.janda.vorwahlguard.domain.port.out.CallEventRecorder
import javax.inject.Inject

/**
 * M2 stub [CallEventRecorder]: discards every event. M3 persists to Room. Must never log —
 * [CallEvent] may carry a raw number (CLAUDE.md §1, §12).
 */
class NoOpCallEventRecorder @Inject constructor() : CallEventRecorder {

    override fun record(event: CallEvent) {
        // Intentionally empty: persistence lands in M3.
    }
}
