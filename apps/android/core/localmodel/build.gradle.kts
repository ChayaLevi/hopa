plugins {
    alias(libs.plugins.android.library)
    id("hopa.ktlint")
}

android {
    namespace = "com.hopa.localmodel"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":inference"))
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
