package com.hopa.contracts.a2ui

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Messages to JSON. Defaults are omitted, so a decoded message encodes back to the same object. */
internal object A2uiEncoder {
    fun message(message: A2uiMessage): JsonObject = buildJsonObject {
        put("version", A2ui.VERSION)
        when (message) {
            is A2uiMessage.CreateSurface -> put(
                "createSurface",
                buildJsonObject {
                    put("surfaceId", message.surfaceId)
                    put("catalogId", A2ui.CATALOG_ID)
                    if (message.components.isNotEmpty()) put("components", components(message.components))
                    message.dataModel?.let { put("dataModel", it) }
                },
            )
            is A2uiMessage.UpdateComponents -> put(
                "updateComponents",
                buildJsonObject {
                    put("surfaceId", message.surfaceId)
                    put("components", components(message.components))
                },
            )
            is A2uiMessage.UpdateDataModel -> put(
                "updateDataModel",
                buildJsonObject {
                    put("surfaceId", message.surfaceId)
                    put("path", message.path)
                    put("value", message.value)
                },
            )
            is A2uiMessage.DeleteSurface -> put(
                "deleteSurface",
                buildJsonObject { put("surfaceId", message.surfaceId) },
            )
        }
    }

    private fun components(components: List<A2uiComponent>) = buildJsonArray {
        components.forEach { add(component(it)) }
    }

    fun component(component: A2uiComponent): JsonObject = buildJsonObject {
        put("id", component.id)
        put("component", component.type)
        when (component) {
            is A2uiComponent.Text -> {
                put("text", text(component.text))
                component.variant?.let { put("variant", it) }
            }
            is A2uiComponent.Row -> layout(component.children, component.justify, component.align)
            is A2uiComponent.Column -> layout(component.children, component.justify, component.align)
            is A2uiComponent.Card -> put("child", component.child)
            is A2uiComponent.Button -> {
                put("child", component.child)
                component.variant?.let { put("variant", it) }
                put("action", event(component.event))
            }
            is A2uiComponent.Divider -> component.axis?.let { put("axis", it) }
            is A2uiComponent.Icon -> put("name", component.name)
            is A2uiComponent.PaymentNotice -> {
                put("amount", text(component.amount))
                component.merchant?.let { put("merchant", text(it)) }
                component.consequence?.let { put("consequence", text(it)) }
                put("acknowledge", text(component.acknowledge))
            }
        }
        component.accessibility?.let { a11y ->
            put(
                "@accessibility",
                buildJsonObject {
                    a11y.label?.let { put("label", text(it)) }
                    a11y.description?.let { put("description", text(it)) }
                },
            )
        }
    }

    private fun JsonObjectBuilder.layout(children: List<String>, justify: String?, align: String?) {
        put("children", buildJsonArray { children.forEach { add(JsonPrimitive(it)) } })
        justify?.let { put("justify", it) }
        align?.let { put("align", it) }
    }

    private fun event(event: A2uiEvent) = buildJsonObject {
        put(
            "event",
            buildJsonObject {
                put("name", event.name)
                if (event.context.isNotEmpty()) {
                    put("context", buildJsonObject { event.context.forEach { (key, value) -> put(key, text(value)) } })
                }
            },
        )
    }

    fun text(text: A2uiText): JsonElement = when (text) {
        is A2uiText.Literal -> JsonPrimitive(text.value)
        is A2uiText.Bound -> buildJsonObject { put("@path", text.path) }
    }
}
