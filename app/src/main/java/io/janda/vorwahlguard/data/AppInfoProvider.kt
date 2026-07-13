package io.janda.vorwahlguard.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the app's own `versionName` for display on the Einstellungen "About" section. `compileSdk`
 * is 37 (gradle/libs.versions.toml), so the single-arg `getPackageInfo(String, Int)` overload is
 * deprecated in favour of the `PackageInfoFlags` overload added in API 33 (confirmed via `javap`
 * against the pinned `android-37.1` platform jar) — `minSdk` is 29, so both paths are needed.
 */
@Singleton
class AppInfoProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun versionName(): String {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        return packageInfo.versionName ?: ""
    }
}
