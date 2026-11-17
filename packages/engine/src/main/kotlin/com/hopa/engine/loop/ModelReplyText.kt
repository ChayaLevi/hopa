package com.hopa.engine.loop

/**
 * Some providers prepend a machine-formatted safety verdict to a reply. It is
 * not an answer and a person should not read it, so it is removed; when nothing
 * else is left, the app says the model stopped instead of printing a verdict.
 */
object ModelReplyText {
    private const val REFUSAL = "The model stopped at its safety check and did not answer."

    private val safety = Regex("""<ds_safety>.*?</ds_safety>""", RegexOption.DOT_MATCHES_ALL)

    fun clean(text: String): String {
        if (!text.contains("<ds_safety>")) {
            return text
        }
        val rest = text.replace(safety, "")
            .trim()
            .removePrefix("Safe")
            .trim()
        return rest.ifBlank { REFUSAL }
    }
}
