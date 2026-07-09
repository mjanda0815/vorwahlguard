// Root build script. Plugins are declared here with `apply false` so that each
// version is resolved once (from the version catalog) and applied per module.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
