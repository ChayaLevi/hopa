package com.hopa.contracts.a2ui

import com.hopa.contracts.a2ui.A2uiFields.Companion.objectAt
import com.hopa.contracts.a2ui.A2uiFields.Companion.pointer
import com.hopa.contracts.a2ui.A2uiFields.Companion.stringAt
import com.hopa.contracts.a2ui.A2uiRefusal.Companion.refuse
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Agent-to-renderer messages, strictly: the HOPA subset of A2UI 1.0 or a refusal. */
internal object A2uiDecoder {
    private val PAYLOADS = setOf("createSurface", "updateComponents", "updateDataModel", "deleteSurface")
    private val REFUSED_PAYLOADS = setOf("callRendererFunction", "agentFunctionResponse")
    private val COMMON = setOf("id", "component", "@accessibility")
    private val REFUSED_COMMON = setOf("@catalogId", "@metadata", "@checks", "weight")
    private val ALIGN = setOf("start", "center", "end", "stretch")
    private val JUSTIFY = setOf("start", "center", "end", "spaceBetween")
    private val ICONS = setOf(
        "check", "close", "error", "help", "info", "lock", "payment", "send", "shoppingCart", "warning",
    )
    private val PROPS = mapOf(
        "Text" to setOf("text", "variant"),
        "Row" to setOf("children", "justify", "align"),
        "Column" to setOf("children", "justify", "align"),
        "Card" to setOf("child"),
        "Button" to setOf("child", "variant", "action"),
        "Divider" to setOf("axis"),
        "Icon" to setOf("name"),
        "PaymentNotice" to setOf("amount", "merchant", "consequence", "acknowledge"),
    )

    fun message(json: JsonObject): A2uiMessage {
        if (json.toString().length > A2ui.MAX_MESSAGE_CHARS) {
            refuse(A2uiCode.VALIDATION_FAILED, "", "the message is too large")
        }
        version(json)
        val payloads = json.keys - "version"
        if (payloads.size != 1) refuse(A2uiCode.VALIDATION_FAILED, "", "a message carries exactly one payload")
        val key = payloads.single()
        if (key in REFUSED_PAYLOADS) refuse(A2uiCode.UNSUPPORTED, "/$key", "$key is not supported by HOPA")
        if (key !in PAYLOADS) refuse(A2uiCode.VALIDATION_FAILED, "/$key", "unknown message $key")
        val body = objectAt(json.getValue(key), "/$key")
        return when (key) {
            "createSurface" -> createSurface(body)
            "updateComponents" -> {
                val fields = A2uiFields(body, "/updateComponents", setOf("surfaceId", "components"))
                A2uiMessage.UpdateComponents(
                    fields.surfaceId(),
                    components(fields.required("components"), fields.at("components")),
                )
            }
            "updateDataModel" -> {
                val fields = A2uiFields(body, "/updateDataModel", setOf("surfaceId", "path", "value"))
                val surfaceId = fields.surfaceId()
                val path = fields.optString("path") ?: "/"
                if (!path.startsWith("/") || path.length > A2ui.MAX_PATH) {
                    refuse(A2uiCode.VALIDATION_FAILED, fields.at("path"), "path must be a JSON Pointer")
                }
                A2uiMessage.UpdateDataModel(surfaceId, fields.required("value"), path)
            }
            else -> A2uiMessage.DeleteSurface(A2uiFields(body, "/deleteSurface", setOf("surfaceId")).surfaceId())
        }
    }

    fun version(json: JsonObject) {
        val version = json["version"] ?: refuse(A2uiCode.VALIDATION_FAILED, "/version", "version is required")
        val value = (version as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (value != A2ui.VERSION) refuse(A2uiCode.UNSUPPORTED, "/version", "only A2UI ${A2ui.VERSION} is supported")
    }

    private fun createSurface(body: JsonObject): A2uiMessage.CreateSurface {
        val fields = A2uiFields(
            body,
            "/createSurface",
            setOf("surfaceId", "catalogId", "sendDataModel", "components", "dataModel"),
            setOf("metadata"),
        )
        val surfaceId = fields.surfaceId()
        if (fields.string("catalogId") != A2ui.CATALOG_ID) {
            refuse(A2uiCode.UNSUPPORTED, fields.at("catalogId"), "only the HOPA catalog is supported")
        }
        if (fields.bool("sendDataModel") == true) {
            refuse(A2uiCode.UNSUPPORTED, fields.at("sendDataModel"), "the data model is never sent back")
        }
        return A2uiMessage.CreateSurface(
            surfaceId = surfaceId,
            components = fields.element("components")?.let { components(it, fields.at("components")) }.orEmpty(),
            dataModel = fields.element("dataModel")?.let { objectAt(it, fields.at("dataModel")) },
        )
    }

    private fun components(element: JsonElement, path: String): List<A2uiComponent> {
        val array = element as? kotlinx.serialization.json.JsonArray
            ?: refuse(A2uiCode.VALIDATION_FAILED, path, "components must be an array")
        if (array.isEmpty() || array.size > A2ui.MAX_COMPONENTS) {
            refuse(A2uiCode.VALIDATION_FAILED, path, "1 to ${A2ui.MAX_COMPONENTS} components")
        }
        val seen = mutableSetOf<String>()
        return array.mapIndexed { index, item ->
            component(objectAt(item, "$path/$index"), "$path/$index").also {
                if (!seen.add(it.id)) refuse(A2uiCode.VALIDATION_FAILED, "$path/$index/id", "${it.id} appears twice")
            }
        }
    }

    private fun component(obj: JsonObject, path: String): A2uiComponent {
        val type = obj["component"]?.let { stringAt(it, "$path/component") }
            ?: refuse(A2uiCode.VALIDATION_FAILED, "$path/component", "component is required")
        val props = PROPS[type]
            ?: refuse(A2uiCode.VALIDATION_FAILED, "$path/component", "$type is not in the HOPA catalog")
        val f = A2uiFields(obj, path, COMMON + props, REFUSED_COMMON)
        val id = f.id("id")
        val a11y = f.element("@accessibility")?.let { accessibility(it, f.at("@accessibility")) }
        return when (type) {
            "Text" -> A2uiComponent.Text(
                id,
                text(f.required("text"), f.at("text")),
                f.enum("variant", setOf("caption", "body")),
                a11y,
            )
            "Row" -> A2uiComponent.Row(id, children(f), f.enum("justify", JUSTIFY), f.enum("align", ALIGN), a11y)
            "Column" -> A2uiComponent.Column(id, children(f), f.enum("justify", JUSTIFY), f.enum("align", ALIGN), a11y)
            "Card" -> A2uiComponent.Card(id, f.id("child"), a11y)
            "Button" -> A2uiComponent.Button(
                id,
                f.id("child"),
                event(f.required("action"), f.at("action")),
                f.enum("variant", setOf("default", "primary", "borderless")),
                a11y,
            )
            "Divider" -> A2uiComponent.Divider(id, f.enum("axis", setOf("horizontal", "vertical")), a11y)
            "Icon" -> A2uiComponent.Icon(
                id,
                f.string("name").also { name ->
                    if (name !in ICONS) refuse(A2uiCode.VALIDATION_FAILED, f.at("name"), "unknown icon $name")
                },
                a11y,
            )
            else -> A2uiComponent.PaymentNotice(
                id = id,
                amount = text(f.required("amount"), f.at("amount")),
                acknowledge = text(f.required("acknowledge"), f.at("acknowledge")),
                merchant = f.element("merchant")?.let { text(it, f.at("merchant")) },
                consequence = f.element("consequence")?.let { text(it, f.at("consequence")) },
                accessibility = a11y,
            )
        }
    }

    private fun children(f: A2uiFields): List<String> {
        val element = f.required("children")
        if (element is JsonObject) {
            refuse(
                A2uiCode.UNSUPPORTED,
                f.at("children"),
                "children templates are not supported",
            )
        }
        val array = f.array("children")
        if (array.isEmpty() || array.size > A2ui.MAX_CHILDREN) {
            refuse(A2uiCode.VALIDATION_FAILED, f.at("children"), "1 to ${A2ui.MAX_CHILDREN} children")
        }
        return array.mapIndexed { index, item ->
            stringAt(item, "${f.at("children")}/$index").also {
                if (!A2ui.ID.matches(
                        it,
                    )
                ) {
                    refuse(A2uiCode.VALIDATION_FAILED, "${f.at("children")}/$index", "bad child id")
                }
            }
        }
    }

    private fun event(element: JsonElement, path: String): A2uiEvent {
        val action = A2uiFields(objectAt(element, path), path, setOf("event"), setOf("functionCall"))
        val event = A2uiFields(action.obj("event"), action.at("event"), setOf("name", "context"), setOf("userMessage"))
        val name = event.string("name")
        if (!A2ui.EVENT_NAME.matches(name)) refuse(A2uiCode.VALIDATION_FAILED, event.at("name"), "bad event name")
        val context = event.element("context")?.let { objectAt(it, event.at("context")) }.orEmpty()
        if (context.size > A2ui.MAX_CONTEXT) {
            refuse(
                A2uiCode.VALIDATION_FAILED,
                event.at("context"),
                "too many context keys",
            )
        }
        return A2uiEvent(
            name,
            context.mapValues { (key, value) ->
                val at = pointer(event.at("context"), key)
                if (!A2ui.CONTEXT_KEY.matches(key)) refuse(A2uiCode.VALIDATION_FAILED, at, "bad context key")
                text(value, at)
            },
        )
    }

    private fun accessibility(element: JsonElement, path: String): A2uiAccessibility {
        val f = A2uiFields(objectAt(element, path), path, setOf("label", "description"))
        return A2uiAccessibility(
            label = f.element("label")?.let { text(it, f.at("label")) },
            description = f.element("description")?.let { text(it, f.at("description")) },
        )
    }

    fun text(element: JsonElement, path: String): A2uiText {
        if (element is JsonObject) {
            if ("@call" in element) {
                refuse(
                    A2uiCode.UNSUPPORTED,
                    pointer(path, "@call"),
                    "function calls are not supported",
                )
            }
            val f = A2uiFields(element, path, setOf("@path"))
            val bound = f.string("@path")
            if (!bound.startsWith("/") || bound.length > A2ui.MAX_PATH) {
                refuse(A2uiCode.VALIDATION_FAILED, f.at("@path"), "@path must be a JSON Pointer")
            }
            return A2uiText.Bound(bound)
        }
        val literal = stringAt(element, path)
        if (literal.length > A2ui.MAX_TEXT) refuse(A2uiCode.VALIDATION_FAILED, path, "text is too long")
        return A2uiText.Literal(literal)
    }
}
