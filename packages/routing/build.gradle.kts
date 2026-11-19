plugins {
    id("hopa.kotlin-jvm")
    id("hopa.ktlint")
}

sourceSets {
    test {
        // Relative to this module: the same fixtures the other builds replay.
        resources.srcDir(file("../../spec"))
    }
}

dependencies {
    testImplementation(project(":codec"))
    api(project(":contracts"))
    api(project(":knowledge"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(testFixtures(project(":knowledge")))
}
