package com.hopa.contracts.a2ui

import com.hopa.contracts.a2ui.A2uiFields.Companion.pointer

/** What a component contains and what it shows, with JSON Pointer suffixes for error paths. */
internal object A2uiTree {
    /** The ids a component contains, with the JSON Pointer suffix of each reference. */
    fun childRefs(component: A2uiComponent): List<Pair<String, String>> = when (component) {
        is A2uiComponent.Card -> listOf("child" to component.child)
        is A2uiComponent.Button -> listOf("child" to component.child)
        is A2uiComponent.Row -> component.children.mapIndexed { index, id -> "children/$index" to id }
        is A2uiComponent.Column -> component.children.mapIndexed { index, id -> "children/$index" to id }
        else -> emptyList()
    }

    fun childIds(component: A2uiComponent): List<String> = childRefs(component).map { it.second }

    /** Every string property, with its JSON Pointer suffix, so bindings can be checked. */
    fun texts(component: A2uiComponent): List<Pair<String, A2uiText>> {
        val own = when (component) {
            is A2uiComponent.Text -> listOf("text" to component.text)
            is A2uiComponent.Button -> component.event.context.map { (key, value) ->
                pointer("action/event/context", key) to value
            }
            is A2uiComponent.PaymentNotice -> listOfNotNull(
                "amount" to component.amount,
                component.merchant?.let { "merchant" to it },
                component.consequence?.let { "consequence" to it },
                "acknowledge" to component.acknowledge,
            )
            else -> emptyList()
        }
        val a11y = listOfNotNull(
            component.accessibility?.label?.let { "@accessibility/label" to it },
            component.accessibility?.description?.let { "@accessibility/description" to it },
        )
        return own + a11y
    }
}
