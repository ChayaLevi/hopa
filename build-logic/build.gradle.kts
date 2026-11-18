// The convention plugins three Gradle builds share: the Android app, the
// Windows sidecar and the backend all include packages/, so the module files
// cannot belong to one of those builds. Versions come from the version catalog
// in apps/android/gradle, the same file they all read.
plugins {
    `kotlin-dsl`
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:${libs.versions.ktlint.get()}")
}
