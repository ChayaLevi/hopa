plugins {
    id("hopa.kotlin-jvm")
    id("hopa.ktlint")
    alias(libs.plugins.kotlin.serialization)
}

sourceSets {
    test {
        // The conformance cases validate decisions and records against spec/.
        resources.srcDir(file("../../spec"))
    }
}

dependencies {
    api(project(":contracts"))
    api(project(":catalog"))
    api(project(":observation"))
    api(project(":runlog"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.json.schema.validator)
    testImplementation(libs.slf4j.nop)
    testImplementation(testFixtures(project(":contracts")))
    testImplementation(testFixtures(project(":catalog")))
}
