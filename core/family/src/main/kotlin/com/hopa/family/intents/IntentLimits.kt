package com.hopa.family.intents

/** Homemind's `LIMITS` in `intents.ts`. */
internal object IntentLimits {
    const val TITLE = 120
    const val TEXT = 500
    const val ANSWER = 1200
    const val QUOTE = 400
    const val ENTRIES = 20
    const val ITEM_IDS = 10
    const val PAST_MS = 2 * 24 * 60 * 60 * 1000L
    const val SPLIT_PARTS = 4
    const val RECENT_LINES = 8
    const val RECENT_LINE = 600
    const val EXTRACTION_PAST_MS = 400 * 24 * 60 * 60 * 1000L
    const val FUTURE_MS = 400 * 24 * 60 * 60 * 1000L
    const val SPAN_MS = 14 * 24 * 60 * 60 * 1000L
    const val MESSAGE = 4000
    const val SOURCE_TEXT = 12000
    const val CONTEXT_ITEMS = 60
    const val CONTEXT_AHEAD_MS = 45 * 24 * 60 * 60 * 1000L
    const val SITUATION_AHEAD_MS = 31 * 24 * 60 * 60 * 1000L
}
