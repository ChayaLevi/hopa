plugins {
    id("hopa.kotlin-jvm")
    id("hopa.ktlint")
    alias(libs.plugins.kotlin.serialization)
    `java-test-fixtures`
}

sourceSets {
    test {
        // The A2UI fixtures in spec/ are the cross-client contract (ADR-0027).
        resources.srcDir(file("../../spec"))
    }
}

dependencies {
    // api: the A2UI types (A2uiSurface, A2uiCodec) expose JsonObject.
    api(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
