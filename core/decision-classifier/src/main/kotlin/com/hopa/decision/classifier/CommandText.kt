package com.hopa.decision.classifier

/**
 * Makes a phrase comparable: lower case, `ё` as `е`, punctuation to spaces,
 * politeness removed, whitespace collapsed.
 *
 * The same function normalizes the app labels the classifier matches against,
 * so "Открой Настройки!" and "открой настройки" are one string.
 */
object CommandText {
    private val PUNCTUATION = Regex("[\\p{Punct}]+")
    private val SPACES = Regex("\\s+")
    private val POLITE = setOf(
        "пожалуйста",
        "плиз",
        "please",
        "можешь",
        "может",
        "could",
        "would",
        "can",
        "you",
        "пожалуиста",
    )

    fun normalize(text: String): String {
        val lower = text.lowercase().replace('ё', 'е')
        val plain = PUNCTUATION.replace(lower, " ")
        val words = SPACES.split(plain).filter { it.isNotEmpty() && it !in POLITE }
        return words.joinToString(" ")
    }
}
