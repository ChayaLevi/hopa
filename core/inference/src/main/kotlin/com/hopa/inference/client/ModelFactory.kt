package com.hopa.inference.client

import com.hopa.contracts.model.ModelPort
import com.hopa.inference.choice.ProviderChoice
import com.hopa.inference.choice.ProviderChoiceRules

class ModelFactory(
    private val rules: ProviderChoiceRules = ProviderChoiceRules(),
) {
    fun create(provider: String, endpoint: String, apiKey: String, modelName: String): ModelPort =
        when (rules.choose(provider, endpoint, apiKey)) {
            ProviderChoice.DEMO -> DemoModelClient()
            ProviderChoice.CLOUD, ProviderChoice.LAN -> OpenAiClient(
                endpoint = endpoint,
                apiKey = apiKey,
                modelName = modelName,
            )
        }
}
