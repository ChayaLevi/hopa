package com.hopa.contracts.a2ui

import com.hopa.contracts.a2ui.A2uiRefusal.Companion.refuse
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The rules JSON Schema cannot say: one tree from `root`, every reference
 * resolves, no component twice, the catalog's allowed parents and children,
 * and every binding names a string in the data model.
 *
 * [where] maps a component id to its JSON Pointer in the message being
 * applied, so a problem points into the message the sender can fix.
 */
internal class A2uiGraph(
    private val components: Map<String, A2uiComponent>,
    private val dataModel: JsonObject,
    private val where: (String) -> String,
) {
    private val visited = mutableSetOf<String>()

    fun check() {
        val root = components.getValue(A2uiSurface.ROOT)
        visit(root, parentType = SURFACE, depth = 1)
        components.keys.firstOrNull { it !in visited }?.let {
            refuse(A2uiCode.VALIDATION_FAILED, where(it), "$it is not reachable from root")
        }
    }

    private fun visit(component: A2uiComponent, parentType: String, depth: Int) {
        visited += component.id
        ALLOWED_PARENTS[component.type]?.let { allowed ->
            if (parentType !in allowed) {
                refuse(A2uiCode.UNALLOWED_PARENT, where(component.id), "${component.type} cannot sit in $parentType")
            }
        }
        if (depth > A2ui.MAX_DEPTH) refuse(A2uiCode.VALIDATION_FAILED, where(component.id), "the tree is too deep")
        for ((suffix, value) in A2uiTree.texts(component)) {
            if (value is A2uiText.Bound) {
                val bound = JsonPointer.get(dataModel, value.path) as? JsonPrimitive
                if (bound == null || !bound.isString) {
                    refuse(
                        A2uiCode.VALIDATION_FAILED,
                        "${where(component.id)}/$suffix",
                        "${value.path} is not a string",
                    )
                }
            }
        }
        for ((suffix, childId) in A2uiTree.childRefs(component)) {
            val at = "${where(component.id)}/$suffix"
            val child = components[childId] ?: refuse(A2uiCode.VALIDATION_FAILED, at, "$childId does not exist")
            ALLOWED_CHILDREN[component.type]?.let { allowed ->
                if (child.type !in allowed) {
                    refuse(A2uiCode.UNALLOWED_CHILD, at, "${component.type} cannot hold ${child.type}")
                }
            }
            if (childId in visited) refuse(A2uiCode.VALIDATION_FAILED, at, "$childId is contained twice")
            visit(child, component.type, depth + 1)
        }
    }

    companion object {
        const val SURFACE = "Surface"

        /** Mirrors `allowedParents` in `spec/a2ui/hopa-catalog.v1.json`. */
        val ALLOWED_PARENTS = mapOf(
            "Card" to setOf(SURFACE),
            "PaymentNotice" to setOf("Column"),
        )

        /** Mirrors `allowedChildren` in `spec/a2ui/hopa-catalog.v1.json`. */
        val ALLOWED_CHILDREN = mapOf(
            "Button" to setOf("Text", "Icon"),
        )
    }
}
