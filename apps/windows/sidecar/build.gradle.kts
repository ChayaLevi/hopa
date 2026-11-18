plugins {
    id("hopa.kotlin-jvm")
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("com.hopa.sidecar.MainKt")
}

dependencies {
    implementation(project(":engine"))
    implementation(project(":knowledge"))
    implementation(project(":models"))
    implementation(project(":net"))
    implementation(project(":contracts"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
