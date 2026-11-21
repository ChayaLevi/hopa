plugins {
    id("hopa.kotlin-jvm")
    id("hopa.ktlint")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":contracts"))

    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
