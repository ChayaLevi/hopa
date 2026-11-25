package com.hopa.contracts.a2ui

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** A string property: a literal, or a JSON Pointer into the data model. */
sealed interface A2uiText {
    data class Literal(val value: String) : A2uiText

    data class Bound(val path: String) : A2uiText
}

data class A2uiAccessibility(
    val label: A2uiText? = null,
    val description: A2uiText? = null,
)

/** A button's agent-side event; HOPA has no function-call actions. */
data class A2uiEvent(
    val name: String,
    val context: Map<String, A2uiText> = emptyMap(),
)

/** The components of the HOPA catalog, `spec/a2ui/hopa-catalog.v1.json`. */
sealed interface A2uiComponent {
    val id: String
    val accessibility: A2uiAccessibility?
    val type: String

    data class Text(
        override val id: String,
        val text: A2uiText,
        val variant: String? = null,
        override val accessibility: A2uiAccessibility? = null,
    ) : A2uiComponent {
        override val type get() = "Text"
    }

    data class Row(
        override val id: String,
        val children: List<String>,
        val justify: String? = null,
        val align: String? = null,
        override val accessibility: A2uiAccessibility? = null,
    ) : A2uiComponent {
        override val type get() = "Row"
    }

    data class Column(
        override val id: String,
        val children: List<String>,
        val justify: String? = null,
        val align: String? = null,
        override val accessibility: A2uiAccessibility? = null,
    ) : A2uiComponent {
        override val type get() = "Column"
    }

    data class Card(
        override val id: String,
        val child: String,
        override val accessibility: A2uiAccessibility? = null,
    ) : A2uiComponent {
        override val type get() = "Card"
    }

    data class Button(
        override val id: String,
        val child: String,
        val event: A2uiEvent,
        val variant: String? = null,
        override val accessibility: A2uiAccessibility? = null,
    ) : A2uiComponent {
        override val type get() = "Button"
    }

    data class Divider(
        override val id: String,
        val axis: String? = null,
        override val accessibility: A2uiAccessibility? = null,
    ) : A2uiComponent {
        override val type get() = "Divider"
    }

    data class Icon(
        override val id: String,
        val name: String,
        override val accessibility: A2uiAccessibility? = null,
    ) : A2uiComponent {
        override val type get() = "Icon"
    }

    /** HOPA's payment block. A renderer keeps approve disabled until [acknowledge] is ticked. */
    data class PaymentNotice(
        override val id: String,
        val amount: A2uiText,
        val acknowledge: A2uiText,
        val merchant: A2uiText? = null,
        val consequence: A2uiText? = null,
        override val accessibility: A2uiAccessibility? = null,
    ) : A2uiComponent {
        override val type get() = "PaymentNotice"
    }
}

/** Agent to renderer: the four A2UI 1.0 messages HOPA uses. */
sealed interface A2uiMessage {
    val surfaceId: String

    /** Always the HOPA catalog and `sendDataModel: false`, so neither is a field. */
    data class CreateSurface(
        override val surfaceId: String,
        val components: List<A2uiComponent> = emptyList(),
        val dataModel: JsonObject? = null,
    ) : A2uiMessage

    data class UpdateComponents(
        override val surfaceId: String,
        val components: List<A2uiComponent>,
    ) : A2uiMessage

    /** A [JsonNull][kotlinx.serialization.json.JsonNull] [value] deletes [path]. */
    data class UpdateDataModel(
        override val surfaceId: String,
        val value: JsonElement,
        val path: String = "/",
    ) : A2uiMessage

    data class DeleteSurface(override val surfaceId: String) : A2uiMessage
}

/** Renderer to agent. */
sealed interface A2uiReply {
    val surfaceId: String
}

data class A2uiAction(
    val name: String,
    override val surfaceId: String,
    val sourceComponentId: String,
    val timestamp: String,
    val context: Map<String, String> = emptyMap(),
) : A2uiReply

data class A2uiError(
    val code: String,
    override val surfaceId: String,
    val message: String,
    val path: String? = null,
) : A2uiReply
