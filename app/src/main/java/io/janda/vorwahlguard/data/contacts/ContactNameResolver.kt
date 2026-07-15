package io.janda.vorwahlguard.data.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a saved contact's display name for an E.164 number, for the Protokoll's
 * contact-bypass rows (issue #85). A targeted `ContactsContract.PhoneLookup` query, not the bulk
 * membership cache [CachedContactsLookup] holds — the log screen may be shown without the
 * screening service ever having warmed that cache.
 *
 * Fails closed like [CachedContactsLookup]: returns `null` (rendered as the generic "Kontakt"
 * label) when `READ_CONTACTS` is not granted, when the number matches no contact, or on any
 * query error — the caller never distinguishes those cases. Must be called off the
 * `onScreenCall()` hot path (it does a `ContentResolver` query); the Protokoll resolves names on
 * a background dispatcher.
 */
@Singleton
class ContactNameResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun nameFor(e164: String): String? {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            return null
        }

        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(e164),
        )
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
            }
        }.getOrNull()
    }
}
