package io.janda.vorwahlguard.data

import io.janda.vorwahlguard.domain.port.out.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** [Clock] adapter backed by the wall clock. */
@Singleton
class SystemClock @Inject constructor() : Clock {

    override fun now(): Instant = Instant.now()
}
