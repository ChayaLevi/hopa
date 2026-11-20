import java.io.File

/**
 * A convention plugin: a JVM module in this repository imports no Android.
 *
 * The rule used to live in `packages/engine/build.gradle.kts`, where it guarded
 * one module; the platform adapters live in `apps/android`, so no module under
 * `packages/` may reach for `android.*` or `androidx.*`. Folding it here means a
 * new package module gets the check by applying `hopa.kotlin-jvm`, and the
 * Python structure check keeps the same rule for CI.
 *
 * The action captures two strings and nothing else: a captured file tree would
 * carry the script object and the configuration cache refuses to store it.
 */
plugins {
    id("org.jetbrains.kotlin.jvm")
}

val sourcesRoot = layout.projectDirectory.dir("src").asFile.absolutePath
val moduleName = project.name

val checkNoAndroidImports = tasks.register("checkNoAndroidImports") {
    group = "verification"
    description = "Fails when a pure JVM module imports android.* or androidx.*."
    inputs.files(fileTree(sourcesRoot) { include("**/*.kt") })
    val root = sourcesRoot
    val module = moduleName
    doLast {
        val offenders = File(root)
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines()
                    .withIndex()
                    .filter { (_, line) ->
                        val text = line.trimStart()
                        text.startsWith("import android.") || text.startsWith("import androidx.")
                    }
                    .map { (index, line) -> "${file.absolutePath}:${index + 1}: ${line.trim()}" }
            }
            .toList()
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "$module must not import Android:\n" + offenders.joinToString("\n"),
            )
        }
    }
}

tasks.named("check") {
    dependsOn(checkNoAndroidImports)
}
