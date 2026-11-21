package com.hopa.decision

/**
 * Chooses which layer, if any, may decide one action class under the person's
 * setting. This is policy, not a winner-take-all: a setting that pins a layer
 * still cannot make it decide payments it never covered.
 */
class DecisionSelector(private val registry: DecisionRegistry) {
    fun select(mode: DecisionMode, actionClass: ActionClass): DecisionSelection {
        val requiresApproval = actionClass == ActionClass.DANGEROUS
        return when (mode) {
            is DecisionMode.Off -> DecisionSelection(null, requiresApproval = requiresApproval)
            is DecisionMode.Automatic -> automatic(actionClass, requiresApproval)
            is DecisionMode.Pinned -> pinned(mode.layerId, actionClass, requiresApproval)
        }
    }

    /**
     * The layers that vote on this step: the applied one first, then every other
     * allowed layer when shadow recording is on.
     */
    fun voters(mode: DecisionMode, actionClass: ActionClass, shadow: Boolean): List<DecisionLayer> {
        val applied = select(mode, actionClass).layer
        val result = mutableListOf<DecisionLayer>()
        if (applied != null) {
            result += applied
        }
        if (shadow) {
            registry.allowedFor(actionClass).filterNot { it === applied }.forEach(result::add)
        }
        return result
    }

    private fun automatic(actionClass: ActionClass, requiresApproval: Boolean): DecisionSelection {
        val layer = registry.defaultFor(actionClass)
        val notice = if (layer == null) {
            "No decision layer is allowed for ${actionClass.name.lowercase()} actions."
        } else {
            null
        }
        return DecisionSelection(layer, notice, requiresApproval)
    }

    private fun pinned(layerId: String, actionClass: ActionClass, requiresApproval: Boolean): DecisionSelection {
        val layer = registry.byId(layerId)
        if (layer == null) {
            return automatic(actionClass, requiresApproval).copy(
                notice = "The layer \"$layerId\" is not in this build; Automatic is used.",
            )
        }
        val metadata = layer.metadata
        val refusal = when {
            metadata.status == LayerStatus.RETIRED -> "has been retired"
            !metadata.covers(actionClass) -> "was not built for ${actionClass.name.lowercase()} actions"
            metadata.conformance != ConformanceStatus.PASSED -> "has not passed conformance"
            else -> null
        }
        if (refusal != null) {
            return automatic(actionClass, requiresApproval).copy(
                notice = "The layer \"$layerId\" $refusal; Automatic is used.",
            )
        }
        return DecisionSelection(layer, requiresApproval = requiresApproval)
    }
}
