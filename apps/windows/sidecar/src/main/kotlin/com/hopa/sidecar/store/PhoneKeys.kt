package com.hopa.sidecar.store

import com.hopa.backend.approval.AnswerSignature
import com.hopa.backend.approval.KeyPins
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * The phones' signing keys this PC pinned on first use (ADR-0032), by gateway
 * device id. A pin never changes by itself: a phone that shows up with another
 * key is denied until the person runs `hopa-sidecar --forget-phone-key`.
 * Public keys only; nothing here is secret.
 */
class PhoneKeys(private val file: Path) : KeyPins {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private var broken = false
    private val keys: MutableMap<String, String> = load()

    /**
     * An unreadable file pins an empty key for everyone: it matches no
     * signature, so every phone allow is denied until the keys are forgotten.
     */
    @Synchronized
    override fun pinned(deviceId: String): ByteArray? =
        if (broken) ByteArray(0) else keys[deviceId]?.let(AnswerSignature::decode)

    @Synchronized
    override fun pin(deviceId: String, key: ByteArray) {
        if (broken || deviceId in keys) return
        keys[deviceId] = AnswerSignature.encode(key)
        persist()
    }

    /** Forget every pinned key; the next signed answer from each phone pins again. */
    @Synchronized
    fun forgetAll(): Int {
        val count = keys.size
        keys.clear()
        broken = false
        Files.deleteIfExists(file)
        return count
    }

    private fun load(): MutableMap<String, String> {
        if (!Files.exists(file)) return mutableMapOf()
        return runCatching {
            val root = json.parseToJsonElement(Files.readString(file)).jsonObject
            root["devices"]?.jsonObject.orEmpty()
                .mapValues { (_, value) -> ((value as JsonObject).getValue("key") as JsonPrimitive).content }
                .toMutableMap()
        }.getOrElse {
            broken = true
            mutableMapOf()
        }
    }

    private fun persist() {
        Files.createDirectories(file.toAbsolutePath().parent)
        val body = buildJsonObject {
            put("version", 1)
            put(
                "devices",
                buildJsonObject {
                    keys.forEach { (deviceId, key) -> put(deviceId, buildJsonObject { put("key", key) }) }
                },
            )
        }
        Files.writeString(file, json.encodeToString(JsonObject.serializer(), body))
    }
}
