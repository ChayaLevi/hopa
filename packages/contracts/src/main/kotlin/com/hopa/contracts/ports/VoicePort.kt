package com.hopa.contracts.ports

interface VoicePort {
    suspend fun transcribe(wav: ByteArray): String

    suspend fun speak(text: String)
}
