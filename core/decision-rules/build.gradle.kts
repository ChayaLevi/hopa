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

tasks.register<JavaExec>("decisionReport") {
    group = "verification"
    description = "Runs every decision layer over spec/decisions and prints the comparison table"
    mainClass.set("com.hopa.decision.rules.DecisionReport")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(file("../../spec/decisions/corpus.v1.json").path)
}
