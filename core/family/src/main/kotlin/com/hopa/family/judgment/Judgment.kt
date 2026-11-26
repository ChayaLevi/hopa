package com.hopa.family.judgment

/**
 * Judgment: how soon the family should see a message from a group, and why (Homemind's
 * `judgment.ts`, `judgeText`). Decided by fixed rules over the message's words, never by the model,
 * so the reason shown is the real reason. The order matters: urgent words win, a sign-up list is
 * shown now, the sender's own "no rush" wins over a date, then dates: today, tomorrow or this week
 * is shown now; next week waits; a message with no date and no request waits.
 *
 * The patterns are Homemind's JavaScript ones, rewritten so Java reads them the same way: `\b` is
 * ASCII-only in JavaScript ([WORD_START], [WORD_END]), `\s` includes the full-width space
 * ([SPACE]), and `^` with `/m` matches after `\n`, `\r`, U+2028 and U+2029 only ([LINE_START]).
 * `spec/fixtures/family/logic-judgment.json` is the contract. Pure.
 */
object Judgment {
    private const val WORD_START = "(?<![A-Za-z0-9_])"
    private const val WORD_END = "(?![A-Za-z0-9_])"
    private const val SPACE = "[\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A" +
        "\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]"
    private const val LINE_START = "(?:\\A|(?<=[\\n\\r\\u2028\\u2029]))"

    private fun words(vararg english: String) = english.joinToString("|") { "$WORD_START$it$WORD_END" }

    private fun rule(reason: JudgmentReason, tier: MessageTier, pattern: String) =
        Triple(reason, tier, Regex(pattern, RegexOption.IGNORE_CASE))

    private val RULES = listOf(
        rule(
            JudgmentReason.URGENT_WORDS,
            MessageTier.URGENT,
            "紧急|马上|立刻|立即|尽快|停课|提前放学|" + words("urgent", "immediately", "right away", "asap", "emergency"),
        ),
        // The list itself ("#接龙", or a line starting with 接龙), not a message that only asks people to sign up.
        rule(
            JudgmentReason.SIGN_UP,
            MessageTier.TIMED,
            "#接龙|$LINE_START$SPACE*接龙|" + words("sign[- ]?up (?:sheet|list)"),
        ),
        rule(
            JudgmentReason.NOT_URGENT,
            MessageTier.SUMMARY,
            "不着急|不急|不用急|不赶时间|" + words("no rush", "not urgent", "no hurry", "whenever you can"),
        ),
        rule(
            JudgmentReason.SOON,
            MessageTier.TIMED,
            "今天|今晚|明天|明早|本周|这周|这个星期|" + words("today", "tonight", "tomorrow", "this week"),
        ),
        rule(
            JudgmentReason.NEXT_WEEK,
            MessageTier.SUMMARY,
            "下周|下星期|下个星期|下个月|" + words("next week", "next month"),
        ),
        rule(
            JudgmentReason.SOON,
            MessageTier.TIMED,
            "(?:周|星期)[一二三四五六日天]|" +
                words("(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)"),
        ),
    )

    /** The tier of a message and why, from its words alone. */
    fun judge(text: String): Judged {
        for ((reason, tier, pattern) in RULES) {
            val match = pattern.find(text) ?: continue
            return Judged(tier, reason, quote = match.value.trim())
        }
        return Judged(MessageTier.SUMMARY, JudgmentReason.NO_DATE)
    }
}
