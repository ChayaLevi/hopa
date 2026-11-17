package com.hopa.engine.ports

interface VoicePort {
    suspend fun transcribe(wav: ByteArray): String

    suspend fun speak(text: String)
}
