plugins {
    id("hopa.kotlin-jvm")
    id("hopa.ktlint")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":contracts"))
    api(project(":inference"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.mockwebserver)
}
