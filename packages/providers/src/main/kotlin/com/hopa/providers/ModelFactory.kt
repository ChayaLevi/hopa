package com.hopa.providers

import com.hopa.contracts.model.ModelPort

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
