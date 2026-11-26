package com.hopa.family.intents

import com.hopa.family.scope.MemberView
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Running a model job (Homemind's `runValidated`): call, parse, validate, and retry once with what
 * was wrong. Nothing the model writes reaches the board without passing a validator. No second try
 * after `auth` or `not_configured`: the same call would fail the same way.
 */
object ModelJobs {
    private val json = Json { isLenient = false }
    private val FENCE_START = Regex("^```(?:json)?${JsText.SPACE}*", RegexOption.IGNORE_CASE)
    private val FENCE_END = Regex("${JsText.SPACE}*```$")
    private val FINAL = setOf(ModelErrorCode.NOT_CONFIGURED, ModelErrorCode.AUTH)
    private const val MAX_ECHO = 4000
    private const val ATTEMPTS = 2

    /** The reply's JSON, allowing a ```json fence around it. Null when it is not JSON at all. */
    fun parseReply(content: String): JsonElement? {
        val text = JsText.trim(content).replace(FENCE_START, "").replace(FENCE_END, "")
        return try {
            json.parseToJsonElement(text)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun <T> runValidated(
        model: ModelPort,
        request: ModelRequest,
        validate: (JsonElement) -> Validation<T>,
    ): JobResult<T> {
        var last = JobResult.Failed(ModelErrorCode.EMPTY.wire, "No reply", attempts = ATTEMPTS)
        var followUp: List<ModelTurn>? = null
        for (attempt in 1..ATTEMPTS) {
            when (val result = model.complete(if (followUp != null) request.copy(followUp = followUp) else request)) {
                is ModelResult.Failed -> {
                    last = JobResult.Failed(result.code.wire, result.message, attempts = ATTEMPTS)
                    if (result.code in FINAL) return last.copy(attempts = attempt)
                    followUp = null
                }
                is ModelResult.Ok -> {
                    val raw = parseReply(result.content)
                    val checked = if (raw == null) {
                        Validation.Invalid(
                            listOf("the reply is not JSON"),
                        )
                    } else {
                        validate(raw)
                    }
                    when (checked) {
                        is Validation.Ok -> return JobResult.Ok(checked.value, attempt)
                        is Validation.Invalid -> {
                            last = JobResult.Failed(
                                code = "invalid",
                                message = "The model gave a reply Homemind could not use",
                                problems = checked.problems,
                                attempts = ATTEMPTS,
                            )
                            followUp = listOf(
                                ModelTurn("assistant", result.content.take(MAX_ECHO)),
                                ModelTurn(
                                    "user",
                                    "That reply was not valid: ${checked.problems.joinToString("; ")}. " +
                                        "Reply again with only the corrected JSON object.",
                                ),
                            )
                        }
                    }
                }
            }
        }
        return last
    }

    /** A member's chat message, as one checked intent. */
    @Suppress("LongParameterList")
    fun runIntent(
        model: ModelPort,
        view: MemberView,
        nowMs: Long,
        lang: Lang,
        text: String,
        recent: List<ChatLine> = emptyList(),
        stores: List<IntentStore> = emptyList(),
        preferences: List<PromptPreference> = emptyList(),
    ): JobResult<ChatIntent> {
        val ctx = IntentPrompts.intentContextFor(view, nowMs, stores)
        val request = IntentPrompts.intentPrompt(view, nowMs, lang, text, recent, stores, preferences)
        return runValidated(model, request) { IntentValidator.validate(it, ctx) }
    }

    /** What a text message says about the family's logistics, checked against the text itself. */
    fun runExtraction(model: ModelPort, nowMs: Long, zone: String, text: String): JobResult<Extraction> {
        if (JsText.trim(text).isEmpty()) return JobResult.Failed("invalid", "Nothing to read", attempts = 0)
        return runValidated(model, IntentPrompts.extractionPrompt(nowMs, zone, text)) {
            ExtractionValidator.validate(it, nowMs, text)
        }
    }
}
