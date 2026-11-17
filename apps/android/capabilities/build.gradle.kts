plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.hopa.capabilities"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // The client-side capabilities (bridge, model clients) are a plain JVM
    // module in packages/; this module adds the Android-only ones.
    api(project(":remote"))
    api(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Wireless-debugging pairing inside the app: adb pair (SPAKE2) and the TLS
    // connection to adbd. Apache-2.0 (dual licensed with GPL).
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    // Pairing derives a key with a Conscrypt method that Android 17 removed from
    // the platform jar. The public library keeps it, and libadb uses it when it
    // is on the classpath (SslUtils.isCustomConscrypt).
    implementation("org.conscrypt:conscrypt-android:2.9.0")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.core)
}
