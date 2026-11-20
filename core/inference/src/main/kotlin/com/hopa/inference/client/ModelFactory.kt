package com.hopa.inference.client

import com.hopa.contracts.model.ModelPort
import com.hopa.inference.choice.ProviderChoice
import com.hopa.inference.choice.ProviderChoiceRules
import com.hopa.inference.local.LocalEngine

class ModelFactory(
    private val rules: ProviderChoiceRules = ProviderChoiceRules(),
    private val localEngine: LocalEngine? = null,
) {
    fun create(provider: String, endpoint: String, apiKey: String, modelName: String): ModelPort =
        when (rules.choose(provider, endpoint, apiKey)) {
            ProviderChoice.DEMO -> DemoModelClient()
            ProviderChoice.ON_DEVICE -> LocalModel(localEngine)
            ProviderChoice.CLOUD, ProviderChoice.LAN -> OpenAiClient(
                endpoint = endpoint,
                apiKey = apiKey,
                modelName = modelName,
            )
        }
}
