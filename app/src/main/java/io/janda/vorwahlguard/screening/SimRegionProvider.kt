package io.janda.vorwahlguard.screening

import android.content.Context
import android.telephony.TelephonyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the default region for [io.janda.vorwahlguard.domain.port.out.NumberNormalizer],
 * per `docs/adr/0009-sim-region-fallback-chain.md`: SIM -> network -> [Locale], uppercased to
 * ISO-3166 alpha-2.
 *
 * [refresh] is the only place this class talks to [TelephonyManager] (Binder IPC); it must be
 * called off the `onScreenCall()` hot path (CLAUDE.md §3 rule 1), e.g. from the screening
 * service's `onCreate()`. [current] only ever reads the cached `@Volatile` field.
 *
 * [current] returns `null` before the first [refresh] — that is the "not yet warmed" sentinel,
 * not an error state; [io.janda.vorwahlguard.domain.port.out.NumberNormalizer] already treats a
 * `null` default region as "cannot resolve a national-format number" per ADR 0009.
 */
@Singleton
class SimRegionProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    @Volatile
    private var cachedRegion: String? = null

    fun current(): String? = cachedRegion

    fun refresh() {
        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

        val resolved = firstNonBlank(
            telephonyManager?.simCountryIso,
            telephonyManager?.networkCountryIso,
            Locale.getDefault().country,
        )

        cachedRegion = resolved?.uppercase(Locale.ROOT)
    }

    private fun firstNonBlank(vararg candidates: String?): String? =
        candidates.firstOrNull { !it.isNullOrBlank() }
}
