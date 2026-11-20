plugins {
    id("hopa.kotlin-jvm")
    id("hopa.ktlint")
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
