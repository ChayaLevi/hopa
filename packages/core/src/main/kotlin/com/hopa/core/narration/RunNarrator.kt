package com.hopa.core.narration

import com.hopa.core.contracts.Action
import com.hopa.core.contracts.RunRecord

/**
 * One sentence per thing HOPA did, in the person's words.
 *
 * Consecutive taps are one line ("Tap Menu, Payments and Confirm") because a
 * person thinks of that as one move, not three. A failed step says what failed
 * and why; a step that never ran says so instead of pretending.
 */
object RunNarrator {
    private const val MAX_TAPS = 3

    fun line(action: Action, executed: Boolean, error: String? = null): String {
        val verb = verb(action)
        return when {
            !error.isNullOrBlank() -> "$verb failed: ${error.trim()}"
            !executed -> "$verb (not run)"
            else -> verb
        }
    }

    /** The whole run, with consecutive taps folded into one line. */
    fun lines(records: List<RunRecord>): List<String> {
        val out = mutableListOf<String>()
        val taps = mutableListOf<String>()
        fun flushTaps() {
            if (taps.isEmpty()) return
            val shown = taps.take(MAX_TAPS)
            val rest = taps.size - shown.size
            val joined = when (shown.size) {
                1 -> shown.first()
                2 -> "${shown[0]} and ${shown[1]}"
                else -> shown.dropLast(1).joinToString(", ") + " and " + shown.last()
            }
            out += if (rest > 0) "Tap $joined, plus $rest more" else "Tap $joined"
            taps.clear()
        }

        for (record in records) {
            val succeeded = record.executed && record.error.isNullOrBlank()
            if (succeeded && record.action.kind in TAP_KINDS) {
                taps += record.action.ref ?: "the screen"
                continue
            }

            flushTaps()
            out += line(record.action, record.executed, record.error)
        }

        flushTaps()
        return out
    }

    private fun verb(action: Action): String = when (action.kind) {
        "ui.launch" -> "Open ${action.app.orEmpty()}"
        "ui.tap", "ui.doubleTap", "ui.longPress" -> "Tap ${action.ref ?: "the screen"}"
        "ui.type" -> "Type into ${action.ref ?: "the field"}"
        "ui.swipe", "ui.scroll" -> "Scroll the screen"
        "ui.back" -> "Go back"
        "ui.home" -> "Go home"
        "web.navigate" -> "Open ${action.url.orEmpty()}"
        "web.read" -> "Read the page"
        else -> action.kind
    }

    private val TAP_KINDS = setOf("ui.tap", "ui.doubleTap", "ui.longPress")
}
