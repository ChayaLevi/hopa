import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

// A plain JVM module: the client engine and the services that include it. No
// Android, no Compose, one JDK. Serialization stays in the module that needs it.
plugins {
    id("org.jetbrains.kotlin.jvm")
    // Every JVM module here is a client package: no Android imports.
    id("hopa.no-android")
}

extensions.configure<KotlinJvmProjectExtension> {
    jvmToolchain(17)
}
