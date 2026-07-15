import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // No kotlin-android plugin — AGP 9's built-in Kotlin compiles Kotlin sources.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.androidx.room)
}

// versionCode as the git commit count: every commit produces a new, strictly increasing build
// number with no manual bookkeeping — the previous hardcoded `1` never changed across installs,
// so the About section always showed "Build 1" no matter how many times the app was rebuilt.
// Requires full git history (CI's actions/checkout defaults to a shallow, single-commit clone —
// harmless there today since CI only assembles a debug APK for verification, never installs or
// distributes it; a real release build must be produced locally, where the full history is
// present, not from that shallow CI checkout).
val gitCommitCount: Int = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
}.standardOutput.asText.get().trim().toInt()

android {
    namespace = "io.janda.vorwahlguard"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.janda.vorwahlguard"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = gitCommitCount
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Optional: only configured when VG_STORE_FILE is present in ~/.gradle/gradle.properties
    // (docs/RELEASE.md §2/§3) — CI and any machine without a keystore must still build the
    // release variant, just unsigned. Never read the keystore or these properties directly;
    // Gradle resolves them, this file only wires the plumbing.
    signingConfigs {
        create("release") {
            val storePath = providers.gradleProperty("VG_STORE_FILE").orNull
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = providers.gradleProperty("VG_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("VG_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("VG_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
                .takeIf { providers.gradleProperty("VG_STORE_FILE").isPresent }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core-domain"))

    // Alignment only, not a new dependency: raises the transitively-resolved
    // kotlinx-serialization (navigation pulls 1.7.3) to the 1.8.1 that room-migration 2.8.4
    // — and therefore MigrationTestHelper on the androidTest classpath, via AGP's consistent
    // resolution — is compiled against. See the version catalog comment.
    implementation(platform(libs.kotlinx.serialization.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)

    // Real-device instrumented tests (app/src/androidTest) — issue #29 store-readiness review.
    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.room.testing)
}
