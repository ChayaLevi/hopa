package com.hopa.inference.choice

/**
 * One decision for every reader of the model settings: the factory that builds
 * the client, the chat port that calls it, and the screens that say which
 * model is in use.
 *
 * A saved key always beats the demo model. The demo exists for one case only:
 * a stub provider that has no key. Before this rule the stub provider won over
 * a saved key, so a key pasted into Settings still produced demo answers while
 * telling the user to paste a key.
 *
 * An on-device provider wins over everything else: there is no endpoint to
 * reach and no key to need, only a model file the engine loads.
 */
class ProviderChoiceRules {
    fun choose(provider: String, endpoint: String, apiKey: String): ProviderChoice {
        val local = endpoint.startsWith("http://")
        return when {
            provider.trim().equals("on-device", ignoreCase = true) -> ProviderChoice.ON_DEVICE
            apiKey.isNotBlank() && local -> ProviderChoice.LAN
            apiKey.isNotBlank() -> ProviderChoice.CLOUD
            provider.equals("stub", ignoreCase = true) -> ProviderChoice.DEMO
            local -> ProviderChoice.LAN
            else -> ProviderChoice.CLOUD
        }
    }
}
