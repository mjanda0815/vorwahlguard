package io.janda.vorwahlguard

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.janda.vorwahlguard.data.events.RetentionPurger
import io.janda.vorwahlguard.di.ApplicationScope
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class VorwahlGuardApp : Application() {

    @Inject lateinit var purger: RetentionPurger

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Launch only — onCreate() must stay fast (CLAUDE.md §3 spirit applies here too, even
        // though this is not the ~5s screening hot path). Housekeeping must never crash the
        // process, so a failed purge is swallowed.
        applicationScope.launch { runCatching { purger.purge() } }
    }
}
