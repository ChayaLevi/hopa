import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
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

// Tests are JUnit 5; the catalog alias points at junit-jupiter. Gradle does
// not add the Platform launcher itself, so the convention does.
val junit = extensions.getByType<VersionCatalogsExtension>().named("libs")
    .findLibrary("junit-platform-launcher")
    .get()
dependencies {
    "testRuntimeOnly"(junit)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
