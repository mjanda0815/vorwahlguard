package io.janda.vorwahlguard.data.contacts

import android.Manifest
import android.app.Application
import android.content.Context
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import io.janda.vorwahlguard.domain.model.PhoneNumber
import io.janda.vorwahlguard.domain.port.out.NumberNormalizer
import io.janda.vorwahlguard.screening.SimRegionProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.fakes.RoboCursor

/**
 * Covers [CachedContactsLookup]'s ADR 0006 fail-closed contract and its normalize-membership
 * build against Robolectric's shadow `ContentResolver`/permission model: a contact number is a
 * member only after a [CachedContactsLookup.refresh] taken while `READ_CONTACTS` is granted, and
 * revoking the permission and refreshing again empties the cache (never leaves a stale populated
 * set). The number normalizer and region provider are faked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class CachedContactsLookupTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val application = context as Application

    private lateinit var normalizer: NumberNormalizer
    private lateinit var simRegionProvider: SimRegionProvider
    private lateinit var lookup: CachedContactsLookup

    private val contactRaw = "0664 1234567"
    private val contactE164 = "+436641234567"
    private val contactNumber = PhoneNumber(contactRaw, contactE164, "AT")

    @Before
    fun setUp() {
        normalizer = mockk()
        every { normalizer.normalize(contactRaw, "AT") } returns contactNumber

        simRegionProvider = mockk()
        every { simRegionProvider.current() } returns "AT"

        lookup = CachedContactsLookup(context, normalizer, simRegionProvider)

        // One contact row for the Phone content query. RoboCursor (not MatrixCursor) is what
        // ShadowContentResolver.setCursor accepts.
        val cursor = RoboCursor().apply {
            setColumnNames(listOf(ContactsContract.CommonDataKinds.Phone.NUMBER))
            setResults(arrayOf(arrayOf<Any>(contactRaw)))
        }
        shadowOf(context.contentResolver).setCursor(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            cursor,
        )
    }

    @Test
    fun `refresh without the contacts permission leaves the cache empty (fail closed)`() {
        shadowOf(application).denyPermissions(Manifest.permission.READ_CONTACTS)

        lookup.refresh()

        assertFalse(lookup.isKnownContact(contactNumber))
    }

    @Test
    fun `refresh with the permission granted makes the normalized contact a known member`() {
        shadowOf(application).grantPermissions(Manifest.permission.READ_CONTACTS)

        lookup.refresh()

        assertTrue(lookup.isKnownContact(contactNumber))
        assertFalse(lookup.isKnownContact(PhoneNumber("0999", "+49999", "DE")))
    }

    @Test
    fun `a withheld number is never a known contact`() {
        shadowOf(application).grantPermissions(Manifest.permission.READ_CONTACTS)
        lookup.refresh()

        assertFalse(lookup.isKnownContact(PhoneNumber.UNKNOWN))
    }

    @Test
    fun `revoking the permission and refreshing empties a previously populated cache`() {
        shadowOf(application).grantPermissions(Manifest.permission.READ_CONTACTS)
        lookup.refresh()
        assertTrue(lookup.isKnownContact(contactNumber))

        shadowOf(application).denyPermissions(Manifest.permission.READ_CONTACTS)
        lookup.refresh()

        assertFalse(lookup.isKnownContact(contactNumber))
    }
}
