package com.hopa.backend.http

import com.hopa.backend.ServerConfig
import com.hopa.contracts.json.HopaJson
import com.hopa.inference.local.ModelManifest
import com.hopa.inference.local.ModelSpec
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.builtins.ListSerializer

/**
 * The models the phone may download.
 *
 * The manifest is `models.v1.json` next to the weights, in the same reviewed
 * shape the app already decodes; the version is a digest of the entries, so an
 * unchanged list answers 304. A server with no directory configured answers an
 * empty manifest, which is the honest state before any weights are placed and
 * exactly what the app's local catalogue fallback expects.
 *
 * A paired device token is required, like the knowledge bundle.
 */
fun Application.modelRoutes(config: ServerConfig) {
    val document = ModelDocument(config.modelsDir)

    routing {
        get("/v1/models") {
            if (!call.paired(config)) {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }
            val etag = document.etag()
            if (call.request.header(HttpHeaders.IfNoneMatch) == etag) {
                call.respond(HttpStatusCode.NotModified)
                return@get
            }
            call.response.header(HttpHeaders.ETag, etag)
            call.response.header(HttpHeaders.CacheControl, "no-cache")
            call.respondText(document.manifest(), ContentType.Application.Json)
        }

        get("/v1/models/{id}") {
            if (!call.paired(config)) {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }
            val spec = document.spec(call.parameters["id"].orEmpty())
            val file = document.file(spec)
            if (spec == null || file == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            val etag = "\"${spec.sha256}\""
            if (call.request.header(HttpHeaders.IfNoneMatch) == etag) {
                call.respond(HttpStatusCode.NotModified)
                return@get
            }
            call.response.header(HttpHeaders.ETag, etag)
            call.respondFile(file)
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.paired(config: ServerConfig): Boolean {
    val token = request.header(HttpHeaders.Authorization)
        ?.removePrefix("Bearer ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    return token?.let(config.auth::deviceFor) != null
}

/** The manifest and the blobs behind it, resolved once per process. */
private class ModelDocument(private val directory: String) {
    private val manifest: ModelManifest = load()

    fun manifest(): String = HopaJson.element.encodeToString(ModelManifest.serializer(), manifest)

    val etagValue: String get() = "\"${manifest.version}\""

    fun etag(): String = etagValue

    fun spec(id: String): ModelSpec? = manifest.models.firstOrNull { it.id == id }

    fun file(spec: ModelSpec?): File? {
        if (spec == null || spec.file.isBlank() || directory.isBlank()) return null
        val root = File(directory).canonicalFile
        val candidate = File(root, spec.file).canonicalFile
        return candidate.takeIf { it.isFile && it.path.startsWith(root.path) }
    }

    private fun load(): ModelManifest {
        if (directory.isBlank()) return ModelManifest(version = EMPTY_VERSION, models = emptyList())
        val document = File(directory, FILE_NAME)
        if (!document.isFile) return ModelManifest(version = EMPTY_VERSION, models = emptyList())
        val parsed = runCatching {
            HopaJson.element.decodeFromString(ModelManifest.serializer(), document.readText())
        }.getOrNull() ?: return ModelManifest(version = EMPTY_VERSION, models = emptyList())
        val digest = MessageDigest.getInstance("SHA-256").digest(
            HopaJson.element
                .encodeToString(ListSerializer(ModelSpec.serializer()), parsed.models)
                .toByteArray(),
        ).joinToString("") { "%02x".format(it) }
        return parsed.copy(version = "sha256:$digest")
    }

    private companion object {
        const val FILE_NAME = "models.v1.json"
        const val EMPTY_VERSION = "sha256:empty"
    }
}
