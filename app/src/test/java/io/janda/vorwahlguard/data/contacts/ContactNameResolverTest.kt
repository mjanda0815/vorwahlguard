package io.janda.vorwahlguard.data.contacts

import android.Manifest
import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.fakes.RoboCursor

/**
 * Covers [ContactNameResolver]'s fail-closed display-time lookup (issue #85) against Robolectric's
 * shadow `ContentResolver`/permission model: a name comes back only when `READ_CONTACTS` is granted
 * and the `PhoneLookup` query returns a non-blank display name; every other case — no permission,
 * no match, a blank name — yields `null` (rendered as the generic "Kontakt" label), and no lookup
 * is attempted at all without the permission.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ContactNameResolverTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val application = context as Application

    private val e164 = "+436641234567"
    private val resolver = ContactNameResolver(context)

    private fun lookupUri(number: String): Uri =
        Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))

    private fun setDisplayName(name: String?) {
        val cursor = RoboCursor().apply {
            setColumnNames(listOf(ContactsContract.PhoneLookup.DISPLAY_NAME))
            setResults(if (name == null) arrayOf() else arrayOf(arrayOf<Any?>(name)))
        }
        shadowOf(context.contentResolver).setCursor(lookupUri(e164), cursor)
    }

    @Test
    fun `returns the display name when the permission is granted and the number matches`() {
        shadowOf(application).grantPermissions(Manifest.permission.READ_CONTACTS)
        setDisplayName("Alex Kontakt")

        assertEquals("Alex Kontakt", resolver.nameFor(e164))
    }

    @Test
    fun `returns null without the contacts permission (fail closed)`() {
        shadowOf(application).denyPermissions(Manifest.permission.READ_CONTACTS)
        setDisplayName("Alex Kontakt")

        assertNull(resolver.nameFor(e164))
    }

    @Test
    fun `returns null when the number matches no contact`() {
        shadowOf(application).grantPermissions(Manifest.permission.READ_CONTACTS)
        setDisplayName(null)

        assertNull(resolver.nameFor(e164))
    }

    @Test
    fun `returns null for a blank display name`() {
        shadowOf(application).grantPermissions(Manifest.permission.READ_CONTACTS)
        setDisplayName("   ")

        assertNull(resolver.nameFor(e164))
    }
}
