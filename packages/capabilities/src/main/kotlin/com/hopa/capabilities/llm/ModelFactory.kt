package com.hopa.capabilities.llm

import com.hopa.core.model.ModelPort

class ModelFactory(
    private val rules: ProviderChoiceRules = ProviderChoiceRules(),
) {
    fun create(
        provider: String,
        endpoint: String,
        apiKey: String,
        modelName: String,
    ): ModelPort = when (rules.choose(provider, endpoint, apiKey)) {
        ProviderChoice.DEMO -> StubModelClient()
        ProviderChoice.CLOUD, ProviderChoice.LAN -> OpenAiClient(
            endpoint = endpoint,
            apiKey = apiKey,
            modelName = modelName,
        )
    }
}
