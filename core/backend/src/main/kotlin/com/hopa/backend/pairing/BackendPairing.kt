package com.hopa.backend.pairing

import com.hopa.contracts.json.HopaJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The one-time answer to a pending mint; the secret lives only in the QR. */
data class PendingSession(
    val sessionId: String,
    val secret: String,
    val qr: String,
    val expiresAt: String,
) {
    /** The secret and QR contents are capabilities until the session ends. */
    override fun toString(): String = "PendingSession(sessionId=$sessionId, secret=<redacted>, qr=<redacted>, " +
        "expiresAt=$expiresAt)"
}

/** What a poller may learn by presenting the session secret. */
data class PendingState(
    val state: String,
    val name: String,
    val publicKey: String,
    val fingerprint: String,
    val expiresAt: String,
)

/** A claim's tokens plus the household key wrap and the phone key it names. */
data class ClaimedSession(
    val credentials: BackendCredentials,
    val wrappedKey: String?,
    val voucherPublicKey: String?,
)

/**
 * The unauthenticated half of the backend: trade a pairing session for device
 * tokens and rotate them later. Neither call puts a secret in a message, and
 * a refusal is reported with the server's own code and text.
 */
class BackendPairing(
    private val gatewayUrl: String,
    private val http: OkHttpClient = OkHttpClient(),
) {
    private val base = BackendUrls.httpBase(gatewayUrl)

    suspend fun claim(link: PairingLink, name: String, publicKey: String, pushToken: String?): BackendCredentials {
        val body = buildJsonObject {
            put("session", link.session)
            put("platform", "android")
            put("name", name)
            put("publicKey", publicKey)
            put("alg", "x25519")
            if (pushToken != null) put("pushToken", pushToken)
        }
        return credentials(
            post(path = "/v1/pair/claim", body = body.toString(), secrets = listOf(link.session)),
            identityRequired = true,
        )
    }

    suspend fun refresh(refreshToken: String): BackendCredentials = credentials(
        post(
            path = "/v1/token/refresh",
            body = buildJsonObject { put("refreshToken", refreshToken) }.toString(),
            secrets = listOf(refreshToken),
        ),
        identityRequired = false,
    )

    /**
     * Mint a pending session for this PC. It carries no household: an
     * authenticated phone chooses one when it vouches for this secret.
     */
    suspend fun mintPending(name: String, publicKey: String): PendingSession {
        val payload = post(
            path = "/v1/pair/pending",
            body = buildJsonObject {
                put("platform", WINDOWS)
                put("name", name)
                put("publicKey", publicKey)
                put("alg", ALG)
            }.toString(),
            secrets = emptyList(),
        )
        val pending = PendingSession(
            sessionId = payload["sessionId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            secret = payload["secret"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            qr = payload["qr"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            expiresAt = payload["expiresAt"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
        if (pending.sessionId.isBlank() || pending.secret.isBlank() ||
            pending.qr.isBlank() || pending.expiresAt.isBlank()
        ) {
            throw IllegalStateException("pending response is incomplete")
        }
        return pending
    }

    /** Poll by the secret the PC minted; the server never echoes it back. */
    suspend fun pollPending(secret: String): PendingState {
        val payload = post(
            path = "/v1/pair/pending/resolve",
            body = buildJsonObject { put("sessionSecret", secret) }.toString(),
            secrets = listOf(secret),
        )
        val state = PendingState(
            state = payload["state"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            name = payload["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            publicKey = payload["publicKey"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            fingerprint = payload["fingerprint"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            expiresAt = payload["expiresAt"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
        if (state.state.isBlank() || state.expiresAt.isBlank()) {
            throw IllegalStateException("pending state is incomplete")
        }
        return state
    }

    /** Claim a vouched pending session and receive the wrap parked for this key. */
    suspend fun claimPending(secret: String, name: String, publicKey: String): ClaimedSession {
        val payload = post(
            path = "/v1/pair/claim",
            body = buildJsonObject {
                put("session", secret)
                put("platform", WINDOWS)
                put("name", name)
                put("publicKey", publicKey)
                put("alg", ALG)
            }.toString(),
            secrets = listOf(secret),
        )
        return ClaimedSession(
            credentials = credentials(payload, identityRequired = true),
            wrappedKey = payload["wrappedKey"]?.jsonPrimitive?.contentOrNull,
            voucherPublicKey = payload["voucherPublicKey"]?.jsonPrimitive?.contentOrNull,
        )
    }

    private fun credentials(payload: JsonObject, identityRequired: Boolean): BackendCredentials {
        val credentials = BackendCredentials(
            deviceId = payload["deviceId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            householdId = payload["householdId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            accessToken = payload["accessToken"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            refreshToken = payload["refreshToken"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            expiresAt = payload["expiresAt"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            gatewayUrl = gatewayUrl,
        )
        if (credentials.accessToken.isBlank() ||
            credentials.refreshToken.isBlank() ||
            credentials.expiresAt.isBlank()
        ) {
            throw IllegalStateException("pairing response is incomplete: missing tokens")
        }
        if (identityRequired && (credentials.deviceId.isBlank() || credentials.householdId.isBlank())) {
            throw IllegalStateException("pairing response is incomplete: missing device identity")
        }
        return credentials
    }

    private suspend fun post(path: String, body: String, secrets: List<String>): JsonObject =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(base + path)
                .post(body.toRequestBody(JSON))
                .build()
            val client = http.newBuilder().callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).build()
            val response = try {
                client.newCall(request).execute()
            } catch (error: IOException) {
                throw BackendPairingFailure.Unavailable(
                    scrub("backend unreachable: ${error.message.orEmpty()}", secrets),
                )
            }
            response.use {
                val text = it.body?.string().orEmpty()
                if (!it.isSuccessful) {
                    val message = scrub(refusal(it.code, text), secrets)
                    throw when {
                        it.code == 429 -> BackendPairingFailure.Unavailable(message)
                        it.code in 400..499 -> BackendPairingFailure.Refused(it.code, message)
                        else -> BackendPairingFailure.Unavailable(message)
                    }
                }
                parse(text)
            }
        }

    private fun refusal(status: Int, text: String): String {
        val error = runCatching { HopaJson.element.parseToJsonElement(text).jsonObject }.getOrNull()
        val code = runCatching { error?.get("code")?.jsonPrimitive?.contentOrNull }.getOrNull()
        val message = runCatching { error?.get("message")?.jsonPrimitive?.contentOrNull }.getOrNull()
        return "pairing refused: ${code ?: "HTTP $status"} ${message.orEmpty()}".trim()
    }

    /** Never echo the response: a malformed body may itself contain tokens. */
    private fun parse(text: String): JsonObject = try {
        HopaJson.element.parseToJsonElement(text).jsonObject
    } catch (error: Exception) {
        throw IllegalStateException("pairing returned no JSON object")
    }

    /** A refusal may come back with the session or token echoed; it never leaves here. */
    private fun scrub(message: String, secrets: List<String>): String = secrets.fold(message) { text, secret ->
        if (secret.isBlank()) text else text.replace(secret, "<redacted>")
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        const val TIMEOUT_SECONDS = 20L
        const val WINDOWS = "windows"
        const val ALG = "x25519"
    }
}
