package com.hopa.family.intents

import com.hopa.family.time.FamilyClock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Homemind's `Checker`: reads one field of a model reply, notes what is wrong (at most 12 problems,
 * in the order the fields are read), and returns a value to go on with. JSON types are read as
 * JavaScript's `typeof` sees them; a missing field and JSON null are both "absent".
 */
internal class Checker(private val nowMs: Long, private val pastMs: Long = IntentLimits.PAST_MS) {
    val problems = mutableListOf<String>()

    fun bad(path: String, what: String) {
        if (problems.size < MAX_PROBLEMS) problems += "$path $what"
    }

    fun text(x: JsElement, path: String, max: Int): String {
        val raw = string(x)
        if (raw == null || JsText.trim(raw).isEmpty()) return "".also { bad(path, "is not text") }
        val value = JsText.trim(raw)
        if (value.length > max) return "".also { bad(path, "is longer than $max") }
        if (CONTROL.containsMatchIn(value)) return "".also { bad(path, "has control characters") }
        return value
    }

    fun optText(x: JsElement, path: String, max: Int): String? =
        if (absent(x) || string(x) == "") null else text(x, path, max)

    /** An ISO datetime with an offset, not too far from now. Normalised to UTC. */
    fun time(x: JsElement, path: String): String {
        val raw = string(x)
        if (raw == null || !ISO.matches(raw)) return "".also { bad(path, "is not an ISO datetime with an offset") }
        val ms = JsDate.parse(raw) ?: return "".also { bad(path, "is not a real date") }
        if (ms < nowMs - pastMs || ms > nowMs + IntentLimits.FUTURE_MS) {
            return "".also {
                bad(
                    path,
                    "is too far from today",
                )
            }
        }
        return FamilyClock.iso(ms)
    }

    fun optTime(x: JsElement, path: String): String? = if (absent(x)) null else time(x, path)

    fun confidence(x: JsElement, path: String): Double {
        val n = number(x)
        if (n == null || !n.isFinite() || n < 0 || n > 1) return 0.0.also { bad(path, "is not a number from 0 to 1") }
        return n
    }

    fun oneOf(x: JsElement, path: String, allowed: List<String>): String {
        val value = string(x)
        if (value == null || value !in allowed) {
            return allowed.first().also {
                bad(path, "is not one of ${allowed.joinToString(", ")}")
            }
        }
        return value
    }

    fun bool(x: JsElement, path: String, fallback: Boolean): Boolean {
        if (absent(x)) return fallback
        val value = (x as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
        if (value == null) return fallback.also { bad(path, "is not true or false") }
        return value
    }

    fun id(x: JsElement, path: String, allowed: List<String>): String {
        val value = string(x)
        if (value == null || value !in allowed) return "".also { bad(path, "is not an id from the board") }
        return value
    }

    fun optId(x: JsElement, path: String, allowed: List<String>): String? = if (absent(
            x,
        )
    ) {
        null
    } else {
        id(x, path, allowed)
    }

    fun ids(x: JsElement, path: String, allowed: List<String>, max: Int): List<String> {
        if (absent(x)) return emptyList()
        if (x !is JsonArray) return emptyList<String>().also { bad(path, "is not a list") }
        if (x.size > max) return emptyList<String>().also { bad(path, "has more than $max entries") }
        return x.mapIndexed { i, v -> id(v, "$path[$i]", allowed) }.distinct()
    }

    fun list(x: JsElement, path: String): List<JsonElement> {
        if (absent(x)) return emptyList()
        if (x !is JsonArray) return emptyList<JsonElement>().also { bad(path, "is not a list") }
        if (x.size > IntentLimits.ENTRIES) {
            return emptyList<JsonElement>().also {
                bad(path, "has more than ${IntentLimits.ENTRIES} entries")
            }
        }
        return x
    }

    companion object {
        private const val MAX_PROBLEMS = 12
        private val CONTROL = Regex("[\\u0000-\\u0008\\u000b\\u000c\\u000e-\\u001f\\u007f]")
        val ISO = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d{1,3})?)?(Z|[+-]\d{2}:\d{2})$""")

        fun absent(x: JsElement): Boolean = x == null || x is JsonNull

        /** A JSON string's content, or null for anything else (JavaScript's `typeof x === 'string'`). */
        fun string(x: JsElement): String? = (x as? JsonPrimitive)?.takeIf { it.isString }?.content

        /** A JSON number, or null (JavaScript's `typeof x === 'number'`). */
        fun number(x: JsElement): Double? =
            (x as? JsonPrimitive)?.takeIf { !it.isString && it !is JsonNull }?.doubleOrNull

        /** A whole JSON number (`Number.isInteger`), or null. */
        fun integer(x: JsElement): Long? = number(x)?.takeIf { it.isFinite() && it == Math.floor(it) }?.toLong()

        fun obj(x: JsElement): JsonObject? = x as? JsonObject
    }
}
