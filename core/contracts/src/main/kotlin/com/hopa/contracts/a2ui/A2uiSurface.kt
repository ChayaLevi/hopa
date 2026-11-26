package com.hopa.contracts.a2ui

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant

/**
 * A surface that passed every rule: a tree from `root`, every binding a string.
 * This is what a renderer draws; it never sees a message that failed.
 */
data class A2uiSurface(
    val id: String,
    val components: Map<String, A2uiComponent>,
    val dataModel: JsonObject,
) {
    val root: A2uiComponent get() = components.getValue(ROOT)

    fun component(id: String): A2uiComponent? = components[id]

    /** The string a [value] shows; bindings are checked before a surface exists, so this never misses. */
    fun text(value: A2uiText): String = when (value) {
        is A2uiText.Literal -> value.value
        is A2uiText.Bound -> (JsonPointer.get(dataModel, value.path) as? JsonPrimitive)?.content.orEmpty()
    }

    fun children(component: A2uiComponent): List<A2uiComponent> = A2uiTree.childIds(component).map {
        components.getValue(it)
    }

    /** The `action` a tap on [buttonId] sends back, or null when it is not a button here. */
    fun tap(buttonId: String, at: Instant): A2uiAction? {
        val button = components[buttonId] as? A2uiComponent.Button ?: return null
        return A2uiAction(
            name = button.event.name,
            surfaceId = id,
            sourceComponentId = button.id,
            timestamp = at.toString(),
            context = button.event.context.mapValues { (_, value) -> text(value) },
        )
    }

    companion object {
        const val ROOT = "root"
    }
}
