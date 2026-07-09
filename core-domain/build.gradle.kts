// Pure-JVM domain core. No Android plugin, no Android dependency — an `import android.*`
// here will not compile, and that is the point (docs/ARCHITECTURE.md).
plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(libs.libphonenumber)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
