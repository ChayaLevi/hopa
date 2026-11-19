plugins {
    id("hopa.kotlin-jvm")
    alias(libs.plugins.kotlin.serialization)
    `java-test-fixtures`
}

// Generated knowledge plus the router that walks it. No ktlint here: most files
// are written by tools/scripts, and reformatting them would break the
// "generated bundle is current" check.
sourceSets {
    test {
        resources.srcDir(file("../../spec"))
    }
    // TestKnowledge loads /knowledge.v1.json from the module's own test resources.
    testFixtures {
        resources.srcDir(file("src/test/resources"))
    }
}

dependencies {
    api(project(":contracts"))

    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(project(":codec"))
}
