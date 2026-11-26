plugins {
    id("hopa.kotlin-jvm")
    id("hopa.ktlint")
    alias(libs.plugins.kotlin.serialization)
}

sourceSets {
    test {
        // The signed-answer vectors in spec/ are the cross-client contract (ADR-0032).
        resources.srcDir(file("../../spec"))
    }
}

dependencies {
    api(project(":contracts"))
    api(project(":inference"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.bcprov)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.mockwebserver)
}
