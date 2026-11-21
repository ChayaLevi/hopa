package com.hopa.decision

/**
 * Every layer the build ships, by id.
 *
 * The registry lists; it does not choose. A duplicate id or a layer that was
 * never conformed is refused at construction, because a misconfigured registry
 * would otherwise only fail on a real phone at a real step.
 */
class DecisionRegistry(layers: List<DecisionLayer>) {
    private val byId: Map<String, DecisionLayer> = LinkedHashMap<String, DecisionLayer>().apply {
        for (layer in layers) {
            val id = layer.metadata.id
            require(put(id, layer) == null) { "duplicate decision layer: $id" }
        }
    }

    fun all(): List<DecisionLayer> = byId.values.toList()

    fun byId(id: String): DecisionLayer? = byId[id.trim()]

    /** Layers that may speak at all: everything except retired ones. */
    fun active(): List<DecisionLayer> = all().filter { it.metadata.status != LayerStatus.RETIRED }

    /**
     * Layers that may decide [actionClass] alone: they cover the class and have
     * passed the conformance kit. A failed or untested layer is never here.
     */
    fun allowedFor(actionClass: ActionClass): List<DecisionLayer> = active().filter { layer ->
        layer.metadata.covers(actionClass) &&
            layer.metadata.conformance == ConformanceStatus.PASSED
    }

    fun defaultFor(actionClass: ActionClass): DecisionLayer? =
        allowedFor(actionClass).firstOrNull { it.metadata.status == LayerStatus.DEFAULT }
}
