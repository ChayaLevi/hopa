package com.hopa.localmodel

import com.hopa.contracts.model.ModelReply
import com.hopa.contracts.model.ModelRequest
import com.hopa.inference.local.LocalEngine
import com.hopa.inference.local.ModelNotLoaded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

/**
 * llama.cpp behind JNI.
 *
 * Everything heavy happens off the main thread: loading a 640 MB file and every
 * token of generation take seconds, and doing that on the UI thread is exactly
 * the ANR the first phone build showed. [complete] therefore switches to the
 * default pool, where a blocked thread is harmless.
 */
class LlamaEngine(
    private val pathProvider: () -> String?,
    private val contextTokens: Int = 4_096,
    private val threads: Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 6),
) : LocalEngine {
    private val _tokens = MutableSharedFlow<String>(extraBufferCapacity = 128)

    /** Pieces as they are produced, for the chat to show while a turn runs. */
    val tokens: SharedFlow<String> = _tokens.asSharedFlow()

    private var handle: Long = 0L
    private var loadedPath: String = ""

    fun cancel() {
        if (libraryLoaded) nativeCancel()
    }

    fun release() {
        if (handle != 0L) {
            nativeFree(handle)
            handle = 0L
            loadedPath = ""
        }
    }

    private fun ensureLoaded(path: String): Boolean {
        if (handle != 0L && loadedPath == path) return true
        release()
        if (!libraryLoaded) return false
        handle = nativeLoad(path, contextTokens, threads)
        if (handle == 0L) return false
        loadedPath = path
        return true
    }

    override suspend fun complete(request: ModelRequest, grammar: String): ModelReply = withContext(Dispatchers.Default) {
        val path = pathProvider() ?: throw ModelNotLoaded(
            "No on-device model is installed. Import a model file in Settings first.",
        )
        if (!ensureLoaded(path)) {
            throw ModelNotLoaded("The on-device engine could not load $path.")
        }
        val prompt = request.messages.joinToString("\n\n") { message ->
            "${message.role}: ${message.content}"
        }
        // A phone CPU writes a few tokens a second; a longer answer is a
        // different feature, not a longer wait.
        val cap = minOf(request.maxTokens, ON_DEVICE_MAX_TOKENS)
        val text = nativeCompleteStream(handle, prompt, cap, grammar) { piece ->
            _tokens.tryEmit(piece)
        }
        ModelReply(text = text)
    }

    private external fun nativeLoad(path: String, contextTokens: Int, threads: Int): Long

    private external fun nativeFree(handle: Long)

    private external fun nativeCancel()

    private external fun nativeCompleteStream(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        grammar: String,
        sink: TokenSink,
    ): String

    private companion object {
        const val ON_DEVICE_MAX_TOKENS = 160

        val libraryLoaded: Boolean = runCatching {
            System.loadLibrary("hopa_llama")
        }.isSuccess
    }
}
