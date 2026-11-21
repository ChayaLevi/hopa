package com.hopa.contracts.model

/**
 * A model whose decoder can be constrained by a grammar.
 *
 * Only on-device engines implement this today: an API client cannot force the
 * server's sampler. The planner asks for the grammar it needs and falls back to
 * a plain completion when the model does not offer one.
 */
interface ModelWithGrammar : ModelPort {
    suspend fun complete(request: ModelRequest, grammar: String): ModelReply
}
