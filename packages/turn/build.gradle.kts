plugins {
    id("hopa.kotlin-jvm")
    id("hopa.ktlint")
}

dependencies {
    api(project(":contracts"))
    api(project(":routing"))
    api(project(":observation"))
    api(project(":records"))
    api(project(":prompt"))
    api(project(":execution"))

    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.core)
}
