package io.janda.vorwahlguard.domain.port.in;

import io.janda.vorwahlguard.domain.model.PhoneNumber;
import io.janda.vorwahlguard.domain.model.ScreeningDecision;
import java.time.Instant;

/**
 * The driving port for the screening hot path. Signature fixed by
 * {@code docs/adr/0006-contacts-bypass-unconditional-priority.md}: {@code isKnownContact} is
 * checked before the rule set is consulted at all.
 *
 * <p>{@code at} is accepted for a future use (recording/timestamps) but is not used by the
 * v1 {@code ScreenIncomingCallService} — see that class's Javadoc.
 */
public interface ScreenIncomingCall {

    ScreeningDecision decide(PhoneNumber number, boolean isKnownContact, Instant at);
}
