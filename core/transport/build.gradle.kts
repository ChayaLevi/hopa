plugins {
    id("hopa.kotlin-jvm")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":codec"))
    api(project(":contracts"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.mockwebserver)
}
