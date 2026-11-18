plugins {
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false
    kotlin("android") version "2.0.21" apply false
    kotlin("plugin.serialization") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}

// The shared convention plugins (build-logic/) carry the JVM toolchain and the
// ktlint gate now. They belong to a composite build, so the Windows sidecar and
// the backend apply the same two plugins to the same modules.
