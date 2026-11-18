package com.hopa.backend.http

import com.hopa.backend.ServerConfig
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The knowledge bundle the app may pick up without a release.
 *
 * The document is the one `tools/scripts/build-knowledge-bundle.py` wrote, so
 * what is reviewed in the repository is exactly what a phone downloads. The
 * version is a digest of the data, which makes a stable ETag: an unchanged
 * catalog answers 304 and the app keeps its copy.
 *
 * A paired device token is required. The bundle is untrusted input on the app
 * side, but it still should not be world-readable.
 */
fun Application.knowledgeRoutes(config: ServerConfig) {
    routing {
        get("/v1/knowledge") {
            val token = call.request.header(HttpHeaders.Authorization)
                ?.removePrefix("Bearer ")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            val identity = token?.let(config.auth::deviceFor)
            if (identity == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }
            val document = KnowledgeDocument
            if (call.request.header(HttpHeaders.IfNoneMatch) == document.etag) {
                call.respond(HttpStatusCode.NotModified)
                return@get
            }
            call.response.header(HttpHeaders.ETag, document.etag)
            call.response.header(HttpHeaders.CacheControl, "no-cache")
            call.respondText(document.text, ContentType.Application.Json)
        }
    }
}

/** The reviewed bundle, read once from the classpath. */
private object KnowledgeDocument {
    val text: String = KnowledgeDocument::class.java
        .getResourceAsStream("/knowledge.v1.json")
        ?.bufferedReader(Charsets.UTF_8)
        ?.use { it.readText() }
        ?: error("knowledge.v1.json is missing from the backend resources")

    val version: String = Json.parseToJsonElement(text)
        .jsonObject["version"]!!
        .jsonPrimitive
        .content

    val etag: String = "\"$version\""
}
