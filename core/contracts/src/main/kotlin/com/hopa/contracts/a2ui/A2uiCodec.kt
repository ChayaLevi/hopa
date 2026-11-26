package com.hopa.contracts.a2ui

import com.hopa.contracts.a2ui.A2uiFields.Companion.objectAt
import com.hopa.contracts.a2ui.A2uiFields.Companion.pointer
import com.hopa.contracts.a2ui.A2uiFields.Companion.stringAt
import com.hopa.contracts.a2ui.A2uiRefusal.Companion.refusable
import com.hopa.contracts.a2ui.A2uiRefusal.Companion.refuse
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * The one way in and out of A2UI JSON. Decoding is strict (ADR-0031): the HOPA
 * catalog, the 1.0 subset, nothing else. A refusal carries the code and the
 * JSON Pointer to send back in an `error` message.
 */
object A2uiCodec {
    private val ERROR_CODES = setOf(
        A2uiCode.VALIDATION_FAILED,
        A2uiCode.UNALLOWED_PARENT,
        A2uiCode.UNALLOWED_CHILD,
        A2uiCode.INTEGRITY_ERROR,
        A2uiCode.UNSUPPORTED,
    )
    private val PATH_CODES = setOf(A2uiCode.VALIDATION_FAILED, A2uiCode.UNALLOWED_PARENT, A2uiCode.UNALLOWED_CHILD)

    fun decode(json: JsonObject): A2uiResult<A2uiMessage> = refusable { A2uiDecoder.message(json) }

    fun encode(message: A2uiMessage): JsonObject = A2uiEncoder.message(message)

    fun decodeReply(json: JsonObject): A2uiResult<A2uiReply> = refusable { reply(json) }

    fun encodeReply(reply: A2uiReply): JsonObject = buildJsonObject {
        put("version", A2ui.VERSION)
        when (reply) {
            is A2uiAction -> put(
                "action",
                buildJsonObject {
                    put("name", reply.name)
                    put("surfaceId", reply.surfaceId)
                    put("sourceComponentId", reply.sourceComponentId)
                    put("timestamp", reply.timestamp)
                    put("context", buildJsonObject { reply.context.forEach { (key, value) -> put(key, value) } })
                },
            )
            is A2uiError -> put(
                "error",
                buildJsonObject {
                    put("code", reply.code)
                    put("surfaceId", reply.surfaceId)
                    if (reply.code in PATH_CODES) put("path", reply.path.orEmpty())
                    put("message", reply.message.take(500))
                },
            )
        }
    }

    /** The `error` message that answers a refused message on [surfaceId]. */
    fun errorFor(surfaceId: String, problem: A2uiProblem): A2uiError =
        A2uiError(problem.code, surfaceId, problem.message, problem.path.takeIf { problem.code in PATH_CODES })

    private fun reply(json: JsonObject): A2uiReply {
        A2uiDecoder.version(json)
        val payloads = json.keys - "version"
        if (payloads.size != 1) refuse(A2uiCode.VALIDATION_FAILED, "", "a reply carries exactly one payload")
        return when (val key = payloads.single()) {
            "action" -> action(objectAt(json.getValue(key), "/action"))
            "error" -> error(objectAt(json.getValue(key), "/error"))
            "callAgentFunction", "rendererFunctionResponse" ->
                refuse(A2uiCode.UNSUPPORTED, "/$key", "$key is not supported by HOPA")
            else -> refuse(A2uiCode.VALIDATION_FAILED, "/$key", "unknown reply $key")
        }
    }

    private fun action(body: JsonObject): A2uiAction {
        val f = A2uiFields(
            body,
            "/action",
            setOf("name", "surfaceId", "sourceComponentId", "timestamp", "context"),
            setOf("userMessage", "metadata"),
        )
        val name = f.string("name")
        if (!A2ui.EVENT_NAME.matches(name)) refuse(A2uiCode.VALIDATION_FAILED, f.at("name"), "bad event name")
        val surfaceId = f.surfaceId()
        val source = f.id("sourceComponentId")
        val timestamp = f.string("timestamp")
        try {
            OffsetDateTime.parse(timestamp)
        } catch (bad: DateTimeParseException) {
            refuse(A2uiCode.VALIDATION_FAILED, f.at("timestamp"), "timestamp must be an ISO 8601 date-time")
        }
        val context = f.obj("context")
        if (context.size > A2ui.MAX_CONTEXT) {
            refuse(
                A2uiCode.VALIDATION_FAILED,
                f.at("context"),
                "too many context keys",
            )
        }
        return A2uiAction(
            name,
            surfaceId,
            source,
            timestamp,
            context.mapValues { (key, value) ->
                stringAt(value, pointer(f.at("context"), key)).also {
                    if (it.length > A2ui.MAX_TEXT) {
                        refuse(
                            A2uiCode.VALIDATION_FAILED,
                            pointer(f.at("context"), key),
                            "too long",
                        )
                    }
                }
            },
        )
    }

    private fun error(body: JsonObject): A2uiError {
        val f = A2uiFields(body, "/error", setOf("code", "surfaceId", "path", "message"))
        val code = f.string("code")
        if (code !in ERROR_CODES) refuse(A2uiCode.VALIDATION_FAILED, f.at("code"), "unknown error code $code")
        val path = if (code in PATH_CODES) f.string("path") else f.optString("path")
        if (code !in PATH_CODES && path != null) {
            refuse(
                A2uiCode.VALIDATION_FAILED,
                f.at("path"),
                "only validation errors have a path",
            )
        }
        return A2uiError(code, f.surfaceId(), f.string("message"), path)
    }
}
