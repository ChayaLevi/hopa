package com.hopa.app.feature.settings

import com.hopa.app.di.AppContainer
import com.hopa.contracts.model.ModelMessage
import com.hopa.contracts.model.ModelRequest
import com.hopa.inference.choice.ProviderState
import com.hopa.inference.choice.ProviderStateResolver
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/**
 * The model side of settings: what a save writes, what a test asks, and whether
 * the stored key is readable. It never touches the screen state; the caller
 * copies the result into it.
 */
internal class ModelSettings(
    private val container: AppContainer,
) {
    private val resolver = ProviderStateResolver()

    suspend fun snapshot(): ModelSnapshot {
        val settings = container.settings.settings.first()
        val key = container.settings.modelKey()
        val keySaved = container.settings.hasModelKey()
        return ModelSnapshot(
            settings = settings,
            key = key,
            keySaved = keySaved,
            providerState = resolver.resolve(settings.provider, settings.endpoint, key),
            error = if (keySaved && key.isBlank()) {
                "The saved key cannot be read on this phone; paste it again"
            } else {
                null
            },
        )
    }

    /**
     * Write every field on the screen, including the key, then read the settings
     * back. The read-back is the point: a key that only exists in the text field
     * used to look configured while the next screen saw nothing.
     */
    suspend fun save(
        provider: String,
        endpoint: String,
        model: String,
        key: String,
        notifyAnswers: Boolean,
        webEnabled: Boolean,
        bridgeUrl: String,
        bridgeKey: String,
        useBridge: Boolean,
    ): String? {
        val trimmedProvider = provider.trim()
        val trimmedEndpoint = endpoint.trim()
        val effectiveProvider = when {
            key.isBlank() -> trimmedProvider.ifBlank { "stub" }
            trimmedProvider.isBlank() || trimmedProvider.equals("stub", true) ->
                if (trimmedEndpoint.startsWith("http://")) "lan" else "openai"
            else -> trimmedProvider
        }
        container.settings.update {
            it.copy(
                provider = effectiveProvider,
                endpoint = trimmedEndpoint,
                model = model.trim(),
                notifyAnswers = notifyAnswers,
                webEnabled = webEnabled,
                bridgeUrl = bridgeUrl.trim(),
                useBridge = useBridge,
            )
        }
        if (key.isNotBlank()) {
            container.settings.setModelKey(key.trim())
        }
        if (bridgeKey.isNotBlank()) {
            container.settings.setBridgeToken(bridgeKey.trim())
        }
        if (key.isNotBlank() && container.settings.modelKey().isBlank()) {
            throw IOException("the key did not reach the Keystore")
        }
        return if (effectiveProvider != trimmedProvider) {
            "Saved as $effectiveProvider: a key turns the demo off"
        } else {
            null
        }
    }

    /**
     * Save first, then ask the saved model one short question. Testing with the
     * typed key alone told the user the model worked while nothing was stored.
     */
    suspend fun test(
        provider: String,
        endpoint: String,
        model: String,
        key: String,
        notifyAnswers: Boolean,
        webEnabled: Boolean,
        bridgeUrl: String,
        bridgeKey: String,
        useBridge: Boolean,
    ): ModelTestResult {
        val message = runCatching {
            save(provider, endpoint, model, key, notifyAnswers, webEnabled, bridgeUrl, bridgeKey, useBridge)
            val current = container.settings.settings.first()
            val storedKey = container.settings.modelKey()
            val state = ProviderStateResolver()
                .resolve(current.provider, current.endpoint, storedKey)
            if (state == ProviderState.DEMO) {
                throw IOException("no key is stored, so this is still the demo model")
            }
            val client = container.modelFactory.create(
                provider = current.provider,
                endpoint = current.endpoint,
                apiKey = storedKey,
                modelName = current.model,
            )
            val reply = withTimeout(30_000) {
                client.complete(
                    ModelRequest(
                        messages = listOf(ModelMessage("user", "Reply with the single word: ready")),
                        maxTokens = 32,
                    ),
                )
            }
            "Model answered: ${reply.text.trim().take(80)}"
        }.getOrElse { error ->
            "Model failed: ${error.message ?: error::class.simpleName}"
        }
        return ModelTestResult(message, message.startsWith("Model answered"))
    }
}
