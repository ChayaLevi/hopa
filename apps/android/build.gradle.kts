plugins {
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false
    kotlin("android") version "2.2.21" apply false
    kotlin("plugin.serialization") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    id("com.google.devtools.ksp") version "2.2.21-2.0.5" apply false
    // Applied by :app only when apps/android/app/google-services.json exists.
    id("com.google.gms.google-services") version "4.4.2" apply false
}

// The shared convention plugins (build-logic/) carry the JVM toolchain and the
// ktlint gate now. They belong to a composite build, so the Windows sidecar and
// the backend apply the same two plugins to the same modules.
