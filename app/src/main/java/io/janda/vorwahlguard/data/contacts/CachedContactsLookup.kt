package io.janda.vorwahlguard.data.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.janda.vorwahlguard.domain.model.PhoneNumber
import io.janda.vorwahlguard.domain.port.out.ContactsLookup
import io.janda.vorwahlguard.domain.port.out.NumberNormalizer
import io.janda.vorwahlguard.screening.SimRegionProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory contacts-membership cache (ADR 0006). [isKnownContact] is a pure set lookup, safe
 * for the `onScreenCall()` hot path (CLAUDE.md §3 rule 1). [refresh] performs the
 * `ContentResolver` query and must only ever be called off that path.
 *
 * Fails closed per ADR 0006's consequences: if `READ_CONTACTS` is not granted (never granted,
 * or revoked after being granted), [refresh] empties the cache rather than leaving a stale
 * populated set.
 */
@Singleton
class CachedContactsLookup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val normalizer: NumberNormalizer,
    private val simRegionProvider: SimRegionProvider,
) : ContactsLookup {

    @Volatile
    private var knownNumbers: Set<String> = emptySet()

    override fun isKnownContact(number: PhoneNumber): Boolean =
        number.isKnown() && knownNumbers.contains(number.e164())

    fun refresh() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS,
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            knownNumbers = emptySet()
            return
        }

        val defaultRegion = simRegionProvider.current()
        val result = mutableSetOf<String>()

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            null,
            null,
            null,
        )?.use { cursor ->
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (numberIndex >= 0) {
                while (cursor.moveToNext()) {
                    val raw = cursor.getString(numberIndex) ?: continue
                    val normalized = normalizer.normalize(raw, defaultRegion)
                    if (normalized.isKnown()) {
                        result.add(normalized.e164())
                    }
                }
            }
        }

        knownNumbers = result
    }
}
