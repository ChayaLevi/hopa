package com.hopa.family.intents

/**
 * Text rules as JavaScript applies them, where Homemind's code relies on them: `\s` and `trim()`
 * include U+3000 and U+FEFF (Java's `\s` and Kotlin's `trim()` differ), and a string's length is
 * counted in UTF-16 units (the same in Kotlin).
 */
internal object JsText {
    /** JavaScript's `\s`, as a regex character class. */
    const val SPACE = "[\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]"

    private val SPACES = Regex("$SPACE+")
    private val SPACE_CHARS: Set<Char> = buildSet {
        addAll(listOf('\t', '\n', '\u000B', '\u000C', '\r', ' ', ' ', ' '))
        for (c in ' '..' ') add(c)
        addAll(listOf(' ', ' ', ' ', ' ', '　', '﻿'))
    }

    fun isSpace(c: Char): Boolean = c in SPACE_CHARS

    fun trim(text: String): String = text.trim(::isSpace)

    /** `text.replace(/\s+/g, ' ')`. */
    fun collapse(text: String): String = text.replace(SPACES, " ")
}
