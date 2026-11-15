package com.hopa.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopa.app.di.AppContainer
import com.hopa.capabilities.bridge.BridgeClient
import com.hopa.capabilities.llm.ProviderStateResolver
import com.hopa.core.model.ModelMessage
import com.hopa.core.model.ModelRequest
import com.hopa.data.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class SettingsViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val resolver = ProviderStateResolver()
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { reload() }
    }

    fun save(
        provider: String,
        endpoint: String,
        model: String,
        key: String,
        notifyAnswers: Boolean,
        webEnabled: Boolean,
        bridgeUrl: String,
        bridgeKey: String,
        useBridge: Boolean,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, saved = false, error = null) }
            // A key the user just typed turns the demo off. Leaving the
            // provider on "stub" while a key is present was the reason chat
            // kept answering with the demo model.
            val trimmedProvider = provider.trim()
            val trimmedEndpoint = endpoint.trim()
            val effectiveProvider = when {
                key.isBlank() -> trimmedProvider.ifBlank { "stub" }
                trimmedProvider.isBlank() || trimmedProvider.equals("stub", true) ->
                    if (trimmedEndpoint.startsWith("http://")) "lan" else "openai"
                else -> trimmedProvider
            }
            runCatching {
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
                reload()
                val note = if (effectiveProvider != trimmedProvider) {
                    "Saved as $effectiveProvider: a key turns the demo off"
                } else {
                    null
                }
                _state.update { it.copy(saved = true, notice = note) }
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "Could not save") }
            }
            _state.update { it.copy(busy = false) }
        }
    }

    /** Ask the configured model one short question, so a wrong key or URL shows up here. */
    fun testModel(provider: String, endpoint: String, model: String, key: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, modelTest = "Testing\u2026") }
            val effectiveKey = key.trim().ifBlank {
                runCatching { container.settings.modelKey() }.getOrDefault("")
            }
            val message = runCatching {
                val client = container.modelFactory.create(
                    provider = provider.trim(),
                    endpoint = endpoint.trim(),
                    apiKey = effectiveKey,
                    modelName = model.trim(),
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
            _state.update { it.copy(busy = false, modelTest = message) }
        }
    }

    fun setTone(value: String) {
        viewModelScope.launch {
            container.settings.update { it.copy(tone = value) }
            reload()
        }
    }

    fun setChattiness(value: String) {
        viewModelScope.launch {
            container.settings.update { it.copy(chattiness = value) }
            reload()
        }
    }

    fun testNotification() {
        if (!com.hopa.app.service.Notifier.permissionGranted(container.context)) {
            _state.update {
                it.copy(notice = "Notifications are off in Android settings")
            }
            return
        }
        val sent = com.hopa.app.service.Notifier.postTest(container.context)
        _state.update {
            it.copy(
                notice = if (sent) {
                    "Test notification sent"
                } else {
                    "Could not show a notification"
                },
            )
        }
    }

    fun pairBridge(url: String, code: String) {
        viewModelScope.launch {
            if (url.isBlank() || code.isBlank()) {
                _state.update { it.copy(bridgeStatus = "Bridge: address and pair code first") }
                return@launch
            }
            _state.update { it.copy(busy = true, bridgeStatus = null) }
            val message = runCatching {
                val session = BridgeClient(url, "").pair(code.trim())
                container.settings.setBridgeToken(session.token)
                container.settings.setBridgeRefresh(session.refresh)
                reload()
                "Bridge paired; the token is in the Keystore"
            }.getOrElse { error -> "Bridge: ${error.message}" }
            _state.update { it.copy(busy = false, bridgeStatus = message) }
        }
    }

    fun testBridge(url: String, token: String) {
        viewModelScope.launch {
            if (url.isBlank()) {
                _state.update { it.copy(bridgeStatus = "Bridge: set an address first") }
                return@launch
            }
            _state.update { it.copy(busy = true, bridgeStatus = null) }
            val effective = token.ifBlank {
                runCatching { container.settings.bridgeToken() }.getOrDefault("")
            }
            val message = runCatching {
                val client = BridgeClient(url, effective)
                client.health()
                val who = client.whoami()
                val devices = who["devices"]?.jsonArray
                    ?.joinToString { it.jsonPrimitive.content }
                val nodes = client.observe()["nodes"]?.jsonArray?.size ?: 0
                "Bridge ok: ${devices ?: "paired"}, $nodes nodes"
            }.getOrElse { error -> "Bridge: ${error.message}" }
            _state.update { it.copy(busy = false, bridgeStatus = message) }
        }
    }

    private suspend fun reload() {
        val settings: AppSettings = container.settings.settings.first()
        val key = container.settings.modelKey()
        val bridgeKey = container.settings.bridgeToken()
        _state.update {
            it.copy(
                settings = settings,
                key = key,
                bridgeKey = bridgeKey,
                providerState = resolver.resolve(settings.provider, settings.endpoint, key),
            )
        }
    }
}
