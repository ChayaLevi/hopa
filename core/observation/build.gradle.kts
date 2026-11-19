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
    api(project(":contracts"))

    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
}
