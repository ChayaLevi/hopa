plugins {
    id("hopa.kotlin-jvm")
    id("hopa.ktlint")
    alias(libs.plugins.kotlin.serialization)
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
    api(project(":contracts"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// The conformance and comparison tests read the shared corpus.
sourceSets {
    test {
        resources.srcDir(file("../../spec"))
    }
}

tasks.register<JavaExec>("decisionReport") {
    group = "verification"
    description = "Runs every shipped decision layer over spec/decisions and prints the table"
    mainClass.set("com.hopa.decision.report.DecisionReport")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(file("../../spec/decisions/corpus.v1.json").path)
}
