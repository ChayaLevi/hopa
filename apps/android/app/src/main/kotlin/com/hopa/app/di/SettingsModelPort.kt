package com.hopa.app.di

import com.hopa.models.ModelFactory
import com.hopa.models.ProviderChoice
import com.hopa.models.ProviderChoiceRules
import com.hopa.contracts.model.ModelPort
import com.hopa.contracts.model.ModelReply
import com.hopa.contracts.model.ModelRequest
import com.hopa.stores.SettingsRepository
import java.io.IOException
import kotlinx.coroutines.flow.first

class SettingsModelPort(
    private val settings: SettingsRepository,
    private val factory: ModelFactory,
    private val rules: ProviderChoiceRules = ProviderChoiceRules(),
) : ModelPort {
    private var cachedKey: String = ""
    private var cached: ModelPort? = null

    override suspend fun complete(request: ModelRequest): ModelReply {
        val current = settings.settings.first()
        val key = settings.modelKey()
        val choice = rules.choose(current.provider, current.endpoint, key)
        if (choice == ProviderChoice.CLOUD && key.isBlank()) {
            throw IOException("no model key configured")
        }
        val signature = "${current.provider}|${current.endpoint}|${current.model}|$key"
        val model = if (signature == cachedKey && cached != null) {
            cached!!
        } else {
            factory.create(current.provider, current.endpoint, key, current.model).also {
                cached = it
                cachedKey = signature
            }
        }
        return model.complete(request)
    }
}
