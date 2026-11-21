plugins {
    id("hopa.kotlin-jvm")
    id("hopa.ktlint")
}

// The corpus lives in spec/ and is read by the conformance test.
sourceSets {
    test {
        resources.srcDir(file("../../spec"))
    }
}

dependencies {
    api(project(":decision"))

    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
