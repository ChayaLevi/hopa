plugins {
    id("hopa.kotlin-jvm")
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("com.hopa.sidecar.MainKt")
}

dependencies {
    implementation(project(":codec"))
    implementation(project(":execution"))
    implementation(project(":observation"))
    implementation(project(":records"))
    implementation(project(":routing"))
    implementation(project(":knowledge"))
    implementation(project(":providers"))
    implementation(project(":transport"))
    implementation(project(":contracts"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
