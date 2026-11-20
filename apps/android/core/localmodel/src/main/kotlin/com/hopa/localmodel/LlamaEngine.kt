package com.hopa.localmodel

import com.hopa.contracts.model.ModelReply
import com.hopa.contracts.model.ModelRequest
import com.hopa.inference.local.LocalEngine
import com.hopa.inference.local.ModelNotLoaded

/**
 * llama.cpp behind JNI.
 *
 * The library is built from a pinned tag by CMake and loaded on first use. The
 * model file is resolved per completion, so installing or deleting one takes
 * effect without restarting the app: a different path releases the old session
 * before the new one loads. One session at a time, CPU only, greedy decoding
 * with an optional grammar, so a plan answer cannot come back as prose.
 */
class LlamaEngine(
    private val pathProvider: () -> String?,
    private val contextTokens: Int = 4_096,
    private val threads: Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 6),
) : LocalEngine {
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

    override suspend fun complete(request: ModelRequest): ModelReply {
        val path = pathProvider() ?: throw ModelNotLoaded(
            "No on-device model is installed. Import a model file in Settings first.",
        )
        if (!ensureLoaded(path)) {
            throw ModelNotLoaded("The on-device engine could not load $path.")
        }
        val prompt = request.messages.joinToString("\n\n") { message ->
            "${message.role}: ${message.content}"
        }
        val text = nativeComplete(handle, prompt, request.maxTokens, "")
        return ModelReply(text = text)
    }

    private external fun nativeLoad(path: String, contextTokens: Int, threads: Int): Long

    private external fun nativeFree(handle: Long)

    private external fun nativeCancel()

    private external fun nativeComplete(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        grammar: String,
    ): String

    private companion object {
        val libraryLoaded: Boolean = runCatching {
            System.loadLibrary("hopa_llama")
        }.isSuccess
    }
}
