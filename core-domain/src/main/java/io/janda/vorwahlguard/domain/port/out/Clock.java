package io.janda.vorwahlguard.domain.port.out;

import java.time.Instant;

/**
 * The current instant, as a port so tests can substitute it. Deliberately named {@code Clock}
 * per PROJECT.md §4 — do not import {@code java.time.Clock} alongside this interface in the
 * same file, the names collide by design.
 */
public interface Clock {

    Instant now();
}
