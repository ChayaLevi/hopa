package com.hopa.inference.client

import com.hopa.contracts.model.ModelPort
import com.hopa.contracts.model.ModelReply
import com.hopa.contracts.model.ModelRequest
import com.hopa.contracts.model.ModelWithGrammar
import com.hopa.inference.local.LocalEngine
import com.hopa.inference.local.ModelNotLoaded

/**
 * The model port that runs on this phone.
 *
 * With no engine it fails with a reason a screen can show, rather than
 * silently reaching for the cloud: a missing model file is a visible mistake,
 * not a fallback.
 */
class LocalModel(
    private val engine: LocalEngine?,
) : ModelWithGrammar {
    override suspend fun complete(request: ModelRequest): ModelReply =
        complete(request, grammar = "")

    override suspend fun complete(request: ModelRequest, grammar: String): ModelReply =
        engine?.complete(request, grammar)
            ?: throw ModelNotLoaded(
                "No on-device model is loaded. Choose a model file in Settings, then try again.",
            )
}
