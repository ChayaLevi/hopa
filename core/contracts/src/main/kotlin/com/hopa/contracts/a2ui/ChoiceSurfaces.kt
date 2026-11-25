package com.hopa.contracts.a2ui

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Choice cards: the question and options of a `Decision.Ask`, one button per
 * option. Buttons, not A2UI's ChoicePicker, so a choice is a single tap with
 * no data model sent back.
 */
object ChoiceSurfaces {
    const val CHOOSE = "choose"
    const val MAX_OPTIONS = 8

    fun surfaceId(turn: String, n: Int): String = A2ui.surfaceId("ask", turn, n)

    fun build(question: String, options: List<String>, surfaceId: String): List<A2uiMessage> {
        require(options.size in 1..MAX_OPTIONS) { "a choice has 1 to $MAX_OPTIONS options" }
        require(options.toSet().size == options.size) { "options must be distinct" }
        val ids = options.indices.map { "option$it" }
        val components = mutableListOf<A2uiComponent>(
            A2uiComponent.Card(A2uiSurface.ROOT, "content"),
            A2uiComponent.Column("content", listOf("question") + ids),
            A2uiComponent.Text("question", A2uiText.Bound("/ask/question")),
        )
        options.forEachIndexed { index, option ->
            components += A2uiComponent.Button(
                "option$index",
                "option${index}Label",
                A2uiEvent(CHOOSE, mapOf("value" to A2uiText.Literal(option))),
            )
            components += A2uiComponent.Text("option${index}Label", A2uiText.Bound("/ask/options/$index"))
        }
        val model = buildJsonObject {
            put(
                "ask",
                buildJsonObject {
                    put("question", question)
                    put("options", buildJsonArray { options.forEach { add(JsonPrimitive(it)) } })
                },
            )
        }
        return listOf(A2uiMessage.CreateSurface(surfaceId, components, model))
    }

    /** The option a tap chose, or null when it is not a tap on one of [options] here. */
    fun chosen(action: A2uiAction, surfaceId: String, options: List<String>): String? {
        if (action.name != CHOOSE || action.surfaceId != surfaceId) return null
        val index = action.sourceComponentId.removePrefix("option").toIntOrNull() ?: return null
        if (action.sourceComponentId != "option$index") return null
        val option = options.getOrNull(index) ?: return null
        return option.takeIf { action.context["value"] == it }
    }
}
