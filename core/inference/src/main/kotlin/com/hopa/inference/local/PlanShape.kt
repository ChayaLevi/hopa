package com.hopa.inference.local

import com.hopa.contracts.json.HopaJson

/**
 * The shape a plan answer must have, and the grammar that forces it.
 *
 * A 2–4B model does not hold a JSON schema in free mode; on the phone the
 * decoder is constrained by [grammar] so the answer cannot be prose, and the
 * loop parses it with [parse]. A rejected answer is retried once with the
 * error appended, never guessed at.
 */
class PlanShape {
    val grammar: String = """
        root   ::= ws "{" ws "\"steps\"" ws ":" ws "[" ws (step (ws "," ws step)*)? ws "]" ws "}" ws
        step   ::= ws "{" ws "\"kind\"" ws ":" ws string ws "," ws "\"target\"" ws ":" ws string ws "," ws "\"reason\"" ws ":" ws string ws "}" ws
        string ::= "\"" ([^"\\] | "\\" ( ["\\/bfnrt] | "u" [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] ) )* "\""
        ws     ::= [ \t\n\r]*
    """.trimIndent()

    fun parse(text: String): PlanAnswer? = runCatching {
        HopaJson.element.decodeFromString(PlanAnswer.serializer(), text.trim())
    }.getOrNull()?.takeIf { it.steps.isNotEmpty() }
}
