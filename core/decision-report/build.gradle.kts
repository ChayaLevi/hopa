plugins {
    id("hopa.kotlin-jvm")
    id("hopa.ktlint")
}

dependencies {
    implementation(project(":decision"))
    implementation(project(":decision-rules"))
    implementation(project(":decision-laix"))

    implementation(libs.kotlinx.coroutines.core)
}

tasks.register<JavaExec>("decisionReport") {
    group = "verification"
    description = "Runs every shipped decision layer over spec/decisions and prints the table"
    mainClass.set("com.hopa.decision.report.DecisionReport")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(file("../../spec/decisions/corpus.v1.json").path)
}
