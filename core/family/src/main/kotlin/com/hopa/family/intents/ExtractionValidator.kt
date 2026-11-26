package com.hopa.family.intents

import com.hopa.family.intents.Checker.Companion.absent
import com.hopa.family.intents.Checker.Companion.obj
import com.hopa.family.time.FamilyClock
import kotlinx.serialization.json.JsonElement

/**
 * Homemind's `validateExtraction`. An old screenshot is still a valid reading, so its dates may be
 * long past (the caller skips what is over). For a text source, an entry whose quote is not in the
 * text was made up: it is dropped and counted. A screenshot's text is in the image, so there is no
 * [sourceText] and nothing to check quotes against.
 */
object ExtractionValidator {
    private val MARKS = Regex("[‹›<>]")
    private val SPACES = Regex("${JsText.SPACE}+")
    private const val MAX_WHO = 60

    /** Whitespace and the data-block escaping do not count when matching a quote against its source. */
    private fun squash(text: String) = text.replace(MARKS, "").replace(SPACES, "")

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun validate(raw: JsonElement?, nowMs: Long, sourceText: String? = null): Validation<Extraction> {
        val o = obj(raw) ?: return Validation.Invalid(listOf("the reply is not a JSON object"))
        val c = Checker(nowMs, IntentLimits.EXTRACTION_PAST_MS)
        val source = sourceText?.takeIf { it.isNotEmpty() }?.let(::squash)
        var dropped = 0
        fun real(quote: String): Boolean {
            if (source == null || quote.isEmpty() || squash(quote) in source) return true
            dropped++
            return false
        }

        val events = c.list(o["events"], "events").flatMapIndexed { i, element ->
            val e = obj(element) ?: return@flatMapIndexed emptyList<Extraction.Event>().also {
                c.bad("events[$i]", "is not an object")
            }
            val p = "events[$i]"
            val start = c.time(e["start"], "$p.start")
            val end = c.optTime(e["end"], "$p.end")
            if (start.isNotEmpty() && !end.isNullOrEmpty() && FamilyClock.millis(end) < FamilyClock.millis(start)) {
                c.bad("$p.end", "is before start")
            }
            val out = Extraction.Event(
                title = c.text(e["title"], "$p.title", IntentLimits.TITLE),
                start = start,
                end = end?.ifEmpty { null },
                confidence = c.confidence(e["confidence"], "$p.confidence"),
                quote = c.text(e["quote"], "$p.quote", IntentLimits.QUOTE),
            )
            if (real(out.quote)) listOf(out) else emptyList()
        }
        val preparations = c.list(o["preparations"], "preparations").flatMapIndexed { i, element ->
            val e = obj(element)
                ?: return@flatMapIndexed emptyList<Extraction.Preparation>().also {
                    c.bad("preparations[$i]", "is not an object")
                }
            val p = "preparations[$i]"
            val due = c.optTime(e["due"], "$p.due")
            val forEvent = c.optText(e["for_event"], "$p.for_event", IntentLimits.TITLE)
            val out = Extraction.Preparation(
                title = c.text(e["title"], "$p.title", IntentLimits.TITLE),
                due = due?.ifEmpty { null },
                forEvent = forEvent?.ifEmpty { null },
                confidence = c.confidence(e["confidence"], "$p.confidence"),
                quote = c.text(e["quote"], "$p.quote", IntentLimits.QUOTE),
            )
            if (real(out.quote)) listOf(out) else emptyList()
        }
        val requests = c.list(o["requests"], "requests").flatMapIndexed { i, element ->
            val e = obj(element) ?: return@flatMapIndexed emptyList<Extraction.Request>().also {
                c.bad("requests[$i]", "is not an object")
            }
            val p = "requests[$i]"
            val due = c.optTime(e["due"], "$p.due")
            val out = Extraction.Request(
                text = c.text(e["text"], "$p.text", IntentLimits.TEXT),
                due = due?.ifEmpty { null },
                confidence = c.confidence(e["confidence"], "$p.confidence"),
                quote = c.text(e["quote"], "$p.quote", IntentLimits.QUOTE),
            )
            if (real(out.quote)) listOf(out) else emptyList()
        }
        var signUp: Extraction.SignUp? = null
        if (!absent(o["sign_up"])) {
            val s = obj(o["sign_up"])
            if (s == null) {
                c.bad("sign_up", "is not an object")
            } else {
                val deadline = c.optTime(s["deadline"], "sign_up.deadline")
                val taken = c.list(s["taken"], "sign_up.taken").mapIndexed { i, t ->
                    val entry = obj(t) ?: return@mapIndexed Extraction.Taken("", "").also {
                        c.bad("sign_up.taken[$i]", "is not an object")
                    }
                    Extraction.Taken(
                        who = c.text(entry["who"], "sign_up.taken[$i].who", MAX_WHO),
                        item = c.text(entry["item"], "sign_up.taken[$i].item", IntentLimits.TITLE),
                    )
                }
                val out = Extraction.SignUp(
                    topic = c.text(s["topic"], "sign_up.topic", IntentLimits.TITLE),
                    deadline = deadline?.ifEmpty { null },
                    taken = taken,
                    confidence = c.confidence(s["confidence"], "sign_up.confidence"),
                    quote = c.text(s["quote"], "sign_up.quote", IntentLimits.QUOTE),
                )
                signUp = out.takeIf { real(it.quote) }
            }
        }
        return if (c.problems.isEmpty()) {
            Validation.Ok(Extraction(events, preparations, requests, signUp, dropped))
        } else {
            Validation.Invalid(c.problems)
        }
    }
}
