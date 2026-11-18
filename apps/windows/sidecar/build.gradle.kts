plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.hopa.sidecar.MainKt")
}

dependencies {
    implementation(project(":engine"))
    implementation(project(":net"))
    implementation(project(":stores"))
    implementation(project(":contracts"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
