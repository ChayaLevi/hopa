package com.hopa.contracts.a2ui

import com.hopa.contracts.a2ui.A2uiRefusal.Companion.refuse
import kotlinx.serialization.json.JsonObject

/**
 * A renderer's surfaces for its lifetime. Each message is applied whole or not
 * at all: a refused update leaves the last good surface on screen. A surface id
 * is never reused, even after a delete (A2UI 1.0 `INTEGRITY_ERROR`).
 */
class A2uiSession {
    private class Draft(val components: Map<String, A2uiComponent>, val dataModel: JsonObject)

    private val live = LinkedHashMap<String, Draft>()
    private val used = mutableSetOf<String>()

    fun apply(json: JsonObject): A2uiOutcome = when (val decoded = A2uiCodec.decode(json)) {
        is A2uiResult.Refused -> A2uiOutcome.Refused(surfaceIdOf(json), decoded.problem)
        is A2uiResult.Ok -> apply(decoded.value)
    }

    fun apply(message: A2uiMessage): A2uiOutcome = try {
        applyOrRefuse(message)
    } catch (refusal: A2uiRefusal) {
        A2uiOutcome.Refused(message.surfaceId, refusal.problem)
    }

    /** Applies [messages] in order; stops at the first refusal. */
    fun applyAll(messages: List<JsonObject>): A2uiOutcome {
        var outcome: A2uiOutcome = A2uiOutcome.Pending("")
        for (message in messages) {
            outcome = apply(message)
            if (outcome is A2uiOutcome.Refused) return outcome
        }
        return outcome
    }

    fun surface(surfaceId: String): A2uiSurface? = live[surfaceId]?.let { draft ->
        if (A2uiSurface.ROOT in draft.components) A2uiSurface(surfaceId, draft.components, draft.dataModel) else null
    }

    private fun applyOrRefuse(message: A2uiMessage): A2uiOutcome {
        val id = message.surfaceId
        return when (message) {
            is A2uiMessage.CreateSurface -> {
                if (id in used) refuse(A2uiCode.INTEGRITY_ERROR, "/createSurface/surfaceId", "$id was already used")
                val draft = Draft(
                    message.components.associateBy { it.id },
                    message.dataModel ?: JsonObject(emptyMap()),
                )
                commit(id, draft, "/createSurface/components", message.components).also { used += id }
            }
            is A2uiMessage.UpdateComponents -> {
                val draft = existing(id, "/updateComponents/surfaceId")
                val merged = LinkedHashMap(draft.components).apply { message.components.forEach { put(it.id, it) } }
                if (merged.size > A2ui.MAX_COMPONENTS) {
                    refuse(A2uiCode.VALIDATION_FAILED, "/updateComponents/components", "too many components")
                }
                commit(id, Draft(merged, draft.dataModel), "/updateComponents/components", message.components)
            }
            is A2uiMessage.UpdateDataModel -> {
                val draft = existing(id, "/updateDataModel/surfaceId")
                val model = JsonPointer.set(draft.dataModel, message.path, message.value)
                    ?: refuse(A2uiCode.VALIDATION_FAILED, "/updateDataModel/path", "${message.path} cannot be written")
                commit(id, Draft(draft.components, model), "/updateDataModel", emptyList())
            }
            is A2uiMessage.DeleteSurface -> {
                existing(id, "/deleteSurface/surfaceId")
                live.remove(id)
                A2uiOutcome.Deleted(id)
            }
        }
    }

    private fun existing(id: String, path: String): Draft =
        live[id] ?: refuse(A2uiCode.INTEGRITY_ERROR, path, "surface $id does not exist")

    private fun commit(id: String, draft: Draft, base: String, sent: List<A2uiComponent>): A2uiOutcome {
        if (A2uiSurface.ROOT !in draft.components) {
            live[id] = draft
            return A2uiOutcome.Pending(id)
        }
        val index = sent.withIndex().associate { (i, component) -> component.id to i }
        A2uiGraph(draft.components, draft.dataModel) { componentId ->
            index[componentId]?.let { "$base/$it" } ?: base
        }.check()
        live[id] = draft
        return A2uiOutcome.Ready(A2uiSurface(id, draft.components, draft.dataModel))
    }

    private fun surfaceIdOf(json: JsonObject): String? = json.values
        .firstNotNullOfOrNull { (it as? JsonObject)?.get("surfaceId") }
        ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
}
