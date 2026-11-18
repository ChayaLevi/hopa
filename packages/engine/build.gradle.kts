plugins {
    id("hopa.kotlin-jvm")
    alias(libs.plugins.kotlin.serialization)
    id("hopa.ktlint")
}

sourceSets {
    test {
        // Relative to this module, so any build that includes it finds the
        // repository's contracts (the Android build and the Windows sidecar).
        resources.srcDir(file("../../contracts"))
    }
}

dependencies {
    api(project(":contracts"))
    api(project(":knowledge"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.json.schema.validator)
    testImplementation(libs.slf4j.nop)
}
