plugins {
    id("hopa.kotlin-jvm")
    id("hopa.ktlint")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
}
