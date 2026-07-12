package io.janda.vorwahlguard.domain.port.out;

import io.janda.vorwahlguard.domain.model.CallEvent;

/**
 * Persists one screening result. CLAUDE.md §3 rule 1: recording happens asynchronously,
 * after {@code respondToCall()} has already been invoked — never on the screening hot path.
 */
public interface CallEventRecorder {

    void record(CallEvent event);
}
