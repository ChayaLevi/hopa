plugins {
    id("hopa.kotlin-jvm")
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("com.hopa.sidecar.MainKt")
}

dependencies {
    implementation(project(":agent"))
    implementation(project(":observation"))
    implementation(project(":runlog"))
    implementation(project(":catalog"))
    implementation(project(":catalog"))
    implementation(project(":inference"))
    implementation(project(":bridge"))
    implementation(project(":contracts"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
