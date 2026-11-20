plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.hopa.share"
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
    api(project(":contracts"))
    implementation(libs.kotlinx.coroutines.android)
}
