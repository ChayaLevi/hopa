plugins {
    id("hopa.kotlin-jvm")
}

dependencies {
    api(project(":contracts"))
    api(project(":engine"))

    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.core)
}
