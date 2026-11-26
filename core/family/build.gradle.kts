plugins {
    id("hopa.kotlin-jvm")
    id("hopa.ktlint")
    alias(libs.plugins.kotlin.serialization)
}

// The family board: the state one device holds and the pure reducer that replays
// the op log into it. No clock, no ids minted, no crypto, no HTTP.
sourceSets {
    test {
        // The board fixtures and the seed live in spec/fixtures/family.
        resources.srcDir(file("../../spec"))
    }
}

dependencies {
    api(project(":contracts"))

    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
