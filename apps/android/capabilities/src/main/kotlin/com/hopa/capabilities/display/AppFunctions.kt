package com.hopa.capabilities.display

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The helper answers `appfn <package>` with the platform's AppFunctions JSON.
 * The card only needs the names, so the tree is walked for `schemaName` values
 * and everything else (services, parameter schemas) is left alone.
 */
object AppFunctions {
    fun names(json: String): List<String> {
        val root = runCatching { Json.parseToJsonElement(json) }.getOrNull() ?: return emptyList()
        val names = mutableListOf<String>()
        collect(root, names)
        return names.distinct()
    }

    private fun collect(element: JsonElement, out: MutableList<String>) {
        when (element) {
            is JsonObject -> element.forEach { (key, value) ->
                if (key == "schemaName") {
                    (value as? JsonArray)?.forEach { item ->
                        (item as? JsonPrimitive)?.contentOrNull
                            ?.takeIf(String::isNotBlank)
                            ?.let(out::add)
                    }
                } else {
                    collect(value, out)
                }
            }

            is JsonArray -> element.forEach { collect(it, out) }
            else -> Unit
        }
    }
}
