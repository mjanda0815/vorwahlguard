package io.janda.vorwahlguard.screening

import android.content.Context
import android.telephony.TelephonyManager
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Covers [SimRegionProvider]'s ADR 0009 fallback chain against Robolectric's shadow
 * [TelephonyManager]: SIM → network → [Locale], each blank value skipped, the result uppercased
 * to ISO-3166 alpha-2, and `null` returned before the first [SimRegionProvider.refresh] (the
 * "not yet warmed" sentinel the normalizer treats as "cannot resolve a national number").
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SimRegionProviderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `current is null before the first refresh`() {
        assertNull(SimRegionProvider(context).current())
    }

    @Test
    fun `the SIM region wins and is uppercased`() {
        shadowOf(telephonyManager).setSimCountryIso("at")
        shadowOf(telephonyManager).setNetworkCountryIso("de")

        val provider = SimRegionProvider(context)
        provider.refresh()

        assertEquals("AT", provider.current())
    }

    @Test
    fun `a blank SIM region falls back to the network region`() {
        shadowOf(telephonyManager).setSimCountryIso("")
        shadowOf(telephonyManager).setNetworkCountryIso("de")

        val provider = SimRegionProvider(context)
        provider.refresh()

        assertEquals("DE", provider.current())
    }

    @Test
    fun `a blank SIM and network region falls back to the default locale country`() {
        shadowOf(telephonyManager).setSimCountryIso("")
        shadowOf(telephonyManager).setNetworkCountryIso("")
        Locale.setDefault(Locale.FRANCE)

        val provider = SimRegionProvider(context)
        provider.refresh()

        assertEquals("FR", provider.current())
    }
}
