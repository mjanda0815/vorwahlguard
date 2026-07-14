package io.janda.vorwahlguard.data

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.Year
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the app's own package metadata for display on the Einstellungen "About" section. `compileSdk`
 * is 37 (gradle/libs.versions.toml), so the single-arg `getPackageInfo(String, Int)` overload is
 * deprecated in favour of the `PackageInfoFlags` overload added in API 33 (confirmed via `javap`
 * against the pinned `android-37.1` platform jar) — `minSdk` is 29, so both paths are needed.
 */
@Singleton
class AppInfoProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun versionName(): String = packageInfo().versionName ?: ""

    /**
     * "0.1.0 (Build 1) · 14. Juli 2026" — versionName, [PackageInfo.getLongVersionCode] (available
     * unconditionally: added in API 28, one below this app's `minSdk` 29) and
     * [PackageInfo.lastUpdateTime], the closest thing `PackageManager` exposes for free to a true
     * build timestamp without adding `BuildConfig` generation and baking a compile-time value into
     * it (which would bust Gradle's build cache on every single build). Formatted with the
     * device's current locale, not hardcoded to German.
     */
    fun displayVersion(): String {
        val info = packageInfo()
        val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
            .withLocale(context.resources.configuration.locales.get(0))
            .format(Instant.ofEpochMilli(info.lastUpdateTime).atZone(ZoneId.systemDefault()))
        return "${info.versionName ?: ""} (Build ${info.longVersionCode}) · $date"
    }

    /** Current year for the About section's "© <year> Martin Janda" copyright line. */
    fun copyrightYear(): Int = Year.now().value

    private fun packageInfo(): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
}
