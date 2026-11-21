plugins {
    id("hopa.kotlin-jvm")
    id("hopa.ktlint")
}

// Vendored LAIX sources under dev/laix/ are copied verbatim from the pinned
// commit (see LAIX.md). They are not ours to restyle, and the structure check
// skips them for the same reason.
ktlint {
    filter {
        exclude("**/dev/laix/**")
    }
}

dependencies {
    api(project(":decision"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

sourceSets {
    test {
        resources.srcDir(file("../../spec"))
    }
}
