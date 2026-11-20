package com.hopa.inference.local

/**
 * Which model to offer first on this phone.
 *
 * The class follows the RAM headroom, not the marketing: a 4 GB phone gets the
 * router, 6 GB the fast tier, 8 GB the balanced tier, 12 GB and above the large
 * one. Inside the class the preferred entry wins when it fits the free space;
 * otherwise the smallest one that fits. Reasoning models are never recommended:
 * they answer slowly by design, which is the opposite of a phone assistant.
 */
class LocalModelRecommendation {
    fun recommend(ramGb: Int, freeBytes: Long, models: List<ModelSpec> = LocalModelCatalogue.models): ModelSpec? {
        val classes = when {
            ramGb >= 12 -> listOf(
                ModelClass.LARGE,
                ModelClass.BALANCED,
                ModelClass.FAST,
                ModelClass.ROUTER,
            )
            ramGb >= 8 -> listOf(ModelClass.BALANCED, ModelClass.FAST, ModelClass.ROUTER)
            ramGb >= 6 -> listOf(ModelClass.FAST, ModelClass.BALANCED, ModelClass.ROUTER)
            else -> listOf(ModelClass.ROUTER, ModelClass.FAST)
        }
        val usable = models.filter {
            it.modelClass != ModelClass.REASONING &&
                it.minRamGb <= ramGb &&
                it.bytes <= freeBytes
        }
        for (modelClass in classes) {
            val inClass = usable.filter { it.modelClass == modelClass }
            val preferred = inClass.firstOrNull { it.preferred }
            if (preferred != null) return preferred
            inClass.minByOrNull { it.bytes }?.let { return it }
        }
        return usable.minByOrNull { it.bytes }
    }
}
