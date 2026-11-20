plugins {
    alias(libs.plugins.android.library)
    id("hopa.ktlint")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.hopa.data"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        minSdk = 29
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Interfaces and models live in packages/data; this module keeps the Room
    // and DataStore implementations.
    api(project(":stores"))
    api(project(":runlog"))
    api(project(":contracts"))
    api(project(":catalog"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    api(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.core)
}
