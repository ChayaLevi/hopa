plugins {
    id("hopa.kotlin-jvm")
    id("hopa.ktlint")
    alias(libs.plugins.kotlin.serialization)
}

sourceSets {
    test {
        // Relative to this module, so any build that includes it finds the
        // repository's fixtures (the Android build, the sidecar and the backend).
        resources.srcDir(file("../../spec"))
    }
}

dependencies {
    api(project(":contracts"))
    api(project(":knowledge"))
    api(project(":routing"))
    api(project(":observation"))
    api(project(":records"))
    api(project(":prompt"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.json.schema.validator)
    testImplementation(libs.slf4j.nop)
    testImplementation(testFixtures(project(":contracts")))
    testImplementation(testFixtures(project(":knowledge")))
}
