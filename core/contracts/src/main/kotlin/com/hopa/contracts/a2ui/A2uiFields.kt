package com.hopa.contracts.a2ui

import com.hopa.contracts.a2ui.A2uiRefusal.Companion.refuse
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Strict reading of one JSON object at [path]. Every key must be [allowed];
 * a key in [refused] is a real A2UI feature HOPA does not take (UNSUPPORTED),
 * anything else is unknown (VALIDATION_FAILED). Keys are checked first, so a
 * refused feature is named even when a required field is also missing.
 */
internal class A2uiFields(
    private val obj: JsonObject,
    private val path: String,
    allowed: Set<String>,
    refused: Set<String> = emptySet(),
) {
    init {
        for (key in obj.keys) {
            when (key) {
                in allowed -> Unit
                in refused -> refuse(A2uiCode.UNSUPPORTED, at(key), "$key is not supported by HOPA")
                else -> refuse(A2uiCode.VALIDATION_FAILED, at(key), "unknown property $key")
            }
        }
    }

    fun at(key: String): String = pointer(path, key)

    fun element(key: String): JsonElement? = obj[key]

    fun required(key: String): JsonElement = obj[key] ?: refuse(A2uiCode.VALIDATION_FAILED, at(key), "$key is required")

    fun string(key: String): String = stringAt(required(key), at(key))

    fun optString(key: String): String? = obj[key]?.let { stringAt(it, at(key)) }

    fun enum(key: String, values: Set<String>): String? = optString(key)?.also {
        if (it !in values) refuse(A2uiCode.VALIDATION_FAILED, at(key), "$key must be one of $values")
    }

    fun id(key: String): String = string(key).also {
        if (!A2ui.ID.matches(it)) refuse(A2uiCode.VALIDATION_FAILED, at(key), "$it is not a HOPA component id")
    }

    fun surfaceId(): String = string("surfaceId").also {
        if (!A2ui.SURFACE_ID.matches(it)) refuse(A2uiCode.VALIDATION_FAILED, at("surfaceId"), "bad surface id")
    }

    fun obj(key: String): JsonObject = objectAt(required(key), at(key))

    fun array(key: String): JsonArray =
        required(key) as? JsonArray ?: refuse(A2uiCode.VALIDATION_FAILED, at(key), "$key must be an array")

    fun bool(key: String): Boolean? = obj[key]?.let {
        val primitive = it as? JsonPrimitive
        if (primitive == null || primitive.isString || primitive.content !in setOf("true", "false")) {
            refuse(A2uiCode.VALIDATION_FAILED, at(key), "$key must be a boolean")
        }
        primitive.content == "true"
    }

    companion object {
        fun pointer(base: String, key: String): String = "$base/" + key.replace("~", "~0").replace("/", "~1")

        fun stringAt(element: JsonElement, path: String): String {
            val primitive = element as? JsonPrimitive
            if (primitive == null || !primitive.isString) refuse(A2uiCode.VALIDATION_FAILED, path, "must be a string")
            return primitive.content
        }

        fun objectAt(element: JsonElement, path: String): JsonObject =
            element as? JsonObject ?: refuse(A2uiCode.VALIDATION_FAILED, path, "must be an object")
    }
}
