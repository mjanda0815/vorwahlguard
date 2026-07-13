package io.janda.vorwahlguard.data.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reports whether `READ_CONTACTS` is currently granted. Mirrors
 * [io.janda.vorwahlguard.screening.CallScreeningRoleProvider]'s rationale: this deliberately
 * reads [ContextCompat.checkSelfPermission] live on every call rather than caching the result —
 * the hazard to avoid is staleness (showing "not granted" right after the user has just granted
 * the permission from the system dialog or app settings), not IPC cost. This class is UI-only
 * (`EinstellungenScreen`'s contacts-bypass toggle), never the `onScreenCall()` hot path.
 */
@Singleton
class ContactsPermissionState @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun isGranted(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CONTACTS,
    ) == PackageManager.PERMISSION_GRANTED
}
