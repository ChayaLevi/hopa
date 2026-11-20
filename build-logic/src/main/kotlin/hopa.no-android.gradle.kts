/**
 * A convention plugin: a JVM module in this repository imports no Android.
 *
 * The rule used to live in `packages/engine/build.gradle.kts`, where it guarded
 * one module; the platform adapters live in `apps/android`, so no module under
 * `packages/` may reach for `android.*` or `androidx.*`. Folding it here means a
 * new package module gets the check by applying `hopa.kotlin-jvm`, and the
 * Python structure check keeps the same rule for CI.
 */
plugins {
    id("org.jetbrains.kotlin.jvm")
}

val checkNoAndroidImports = tasks.register("checkNoAndroidImports") {
    group = "verification"
    description = "Fails when a pure JVM module imports android.* or androidx.*."
    val sources = fileTree("src") { include("**/*.kt") }
    inputs.files(sources)
    doLast {
        val offenders = sources.files.flatMap { file ->
            file.readLines()
                .withIndex()
                .filter { (_, line) ->
                    val text = line.trimStart()
                    text.startsWith("import android.") || text.startsWith("import androidx.")
                }
                .map { (index, line) ->
                    "${file.relativeTo(projectDir)}:${index + 1}: ${line.trim()}"
                }
        }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "${project.name} must not import Android:\n" + offenders.joinToString("\n"),
            )
        }
    }
}

tasks.named("check") {
    dependsOn(checkNoAndroidImports)
}
