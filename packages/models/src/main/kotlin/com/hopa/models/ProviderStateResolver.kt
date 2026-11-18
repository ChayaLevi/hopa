package com.hopa.models

/**
 * The state a screen shows. It is derived from the same [ProviderChoiceRules]
 * the client factory uses, so "Cloud model" in Settings means the chat will
 * actually call the cloud.
 */
class ProviderStateResolver(
    private val rules: ProviderChoiceRules = ProviderChoiceRules(),
) {
    fun resolve(provider: String, endpoint: String, apiKey: String): ProviderState =
        when (rules.choose(provider, endpoint, apiKey)) {
            ProviderChoice.DEMO -> ProviderState.DEMO
            ProviderChoice.LAN -> ProviderState.LAN
            ProviderChoice.CLOUD ->
                if (apiKey.isBlank()) ProviderState.NO_MODEL else ProviderState.CLOUD
        }
}
