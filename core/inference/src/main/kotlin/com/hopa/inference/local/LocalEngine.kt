package com.hopa.inference.local

import com.hopa.contracts.model.ModelReply
import com.hopa.contracts.model.ModelRequest

/**
 * A model that answers without leaving the phone.
 *
 * The interface is deliberately small and Android-free: the engine that loads
 * a file and runs tokens lives in `:core:localmodel`, while the choice, the
 * prompt budget and the grammar stay in this module and are tested on the JVM.
 */
interface LocalEngine {
    suspend fun complete(request: ModelRequest, grammar: String = ""): ModelReply
}
