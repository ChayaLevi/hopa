package com.hopa.sidecar

import com.hopa.sidecar.Protocol.asObject
import com.hopa.sidecar.Protocol.text
import com.hopa.sidecar.Protocol.type
import com.hopa.backend.link.BackendClient
import com.hopa.backend.link.BackendLink
import com.hopa.backend.pairing.BackendCredentials
import com.hopa.backend.pairing.BackendPairing as CorePairing
import com.hopa.contracts.chat.Attachment
import com.hopa.contracts.json.HopaJson
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun main(args: Array<String>) {
    val io = Stdio()
    val device = HostDevice(io)
    val dataDir = System.getenv("HOPA_DATA_DIR")
        ?.let { Path.of(it) }
        ?: Path.of(System.getProperty("user.home"), ".hopa", "windows")
    val store = ChatStore(dataDir.resolve("conversations.json"))
    val approvals = PendingApprovals(dataDir.resolve("approvals.json"))
    val records = RecordStore(dataDir.resolve("records.json"))
    val config = RuntimeConfig()
    val approval = HostApproval(io, approvals)
    val attachments = Attachments(dataDir.resolve("attachments"))
    val engine = Engine(io, device, approval, store, records, config)
    val backendScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val sessionPath = dataDir.resolve("backend-session.json")
    val sessionStore = BackendSessionStore(sessionPath)
    val runner = EngineTaskRunner(
        startTurn = { id, goal -> engine.startTurn(id, goal, conversationId = null) },
        cancelTurn = engine::cancel,
    )
    engine.onTurnEnd = runner::complete
    val gateways = GatewayHost(
        io = io,
        store = store,
        records = records,
        sessionStore = sessionStore,
        runner = runner,
        scope = backendScope,
    )

    fun startStoredSession(): Boolean {
        val session = sessionStore.load()
        if (session == null) {
            if (Files.exists(sessionPath)) {
                io.log("backend admission: stored session is unreadable; encrypted tasks stay off until this PC pairs again")
            } else {
                io.log("backend admission: none; encrypted tasks stay off until a phone pairs this PC")
            }
            return false
        }
        val contentKey = backendContentKey(session)
        if (contentKey == null) {
            io.log("backend admission: found device ${session.deviceId}, but its content key is invalid; encrypted tasks stay off")
            return false
        }
        io.log("backend admission: found device ${session.deviceId} in household ${session.householdId}")
        gateways.start(session, contentKey)
        return true
    }

    // `--pair-backend <url>` admits synchronously before the stdio loop, so a
    // host can launch the sidecar and watch stderr for the QR. A failure keeps
    // an existing stored session and lets the protocol command retry later.
    val pairUrl = pairBackendUrl(args)
    var started = false
    if (pairUrl != null) {
        try {
            runBlocking { BackendPairing(dataDir).admit(pairUrl, "Windows PC") }
            started = startStoredSession()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: Exception) {
            io.log("backend admission failed (${failed.javaClass.simpleName})")
        }
    }
    if (!started) startStoredSession()

    io.send(Protocol.ready())
    val denied = approvals.restoreAndDeny()
    if (denied > 0) {
        io.log("denied $denied pending approval(s) on restart")
    }
    io.log("ready (protocol v${Protocol.VERSION}), conversations at $dataDir, runs=${records.count()}")
    while (true) {
        val line = readLine() ?: break
        if (line.isBlank()) continue
        val message = line.asObject() ?: continue
        when (message.type()) {
            "hello" -> io.send(Protocol.ready())
            "conversations.list" ->
                io.send(Protocol.conversationList(
                    message.text("id"),
                    store.list(message.text("includeArchived").toBoolean()),
                ))

            "conversation.pin" ->
                io.send(Protocol.conversationOpened(
                    message.text("id"),
                    store.setPinned(message.text("conversationId"), message.text("pinned").toBoolean()),
                ))

            "conversation.archive" ->
                io.send(Protocol.conversationOpened(
                    message.text("id"),
                    store.setArchived(message.text("conversationId"), message.text("archived").toBoolean()),
                ))

            "conversation.create" ->
                io.send(Protocol.conversationOpened(message.text("id"), store.create()))

            "conversation.open" ->
                io.send(Protocol.conversationOpened(message.text("id"), store.get(message.text("conversationId"))))

            "conversation.search" ->
                io.send(Protocol.conversationList(message.text("id"), store.search(message.text("query"))))

            "conversation.rename" ->
                io.send(Protocol.conversationOpened(
                    message.text("id"),
                    store.rename(message.text("conversationId"), message.text("title")),
                ))

            "conversation.delete" ->
                io.send(Protocol.conversationDeleted(
                    message.text("id"),
                    store.delete(message.text("conversationId")),
                ))

            "conversation.export" ->
                io.send(Protocol.conversationExported(
                    message.text("id"),
                    store.get(message.text("conversationId")),
                ))

            "configure" -> {
                config.update(message)
                io.log("configured: ${config.summary()}")
                io.send(Protocol.configured(message.text("id"), config.useBridge, config.modelProvider))
            }

            "turn.start" -> {
                val attachment = (message["attachment"] as? JsonObject)?.let {
                    HopaJson.element.decodeFromJsonElement(Attachment.serializer(), it)
                }
                engine.startTurn(
                    id = message.text("id"),
                    goal = message.text("goal"),
                    conversationId = message.text("conversationId").ifBlank { null },
                    attachment = attachment,
                )
            }

            "file.attach" -> {
                val path = message.text("path")
                val result = runCatching { attachments.accept(Path.of(path)) }
                io.send(
                    Protocol.fileAttached(
                        id = message.text("id"),
                        attachment = result.getOrNull(),
                        error = result.exceptionOrNull()?.message,
                    ),
                )
            }

            "file.remove" -> {
                attachments.remove(message.text("path"))
                io.send(Protocol.fileRemoved(message.text("id"), true))
            }

            "turn.cancel" -> {
                val turnId = message.text("id")
                if (turnId.isBlank()) engine.cancel() else engine.cancel(turnId)
            }

            "device.result" -> device.resolve(message.text("id"), message)
            "approval.result" -> approval.resolve(message.text("id"), message.text("allow").toBoolean())

            // Pairing polls and prints the QR on stderr, so it must not block
            // the stdin loop. The reply carries identifiers only.
            "backend.pair" -> {
                val url = message.text("url").trim()
                val name = message.text("name").trim().ifBlank { "Windows PC" }
                backendScope.launch {
                    val reply = if (url.isBlank()) {
                        Protocol.backendPair(ok = false, error = "IllegalArgumentException")
                    } else {
                        try {
                            BackendPairing(dataDir).admit(url, name)
                            val saved = sessionStore.load()
                                ?: throw IllegalStateException("admission did not store a session")
                            val key = backendContentKey(saved)
                                ?: throw IllegalStateException("admission stored an invalid content key")
                            gateways.start(saved, key)
                            Protocol.backendPair(ok = true)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failed: Exception) {
                            io.log("backend pairing failed (${failed.javaClass.simpleName})")
                            Protocol.backendPair(ok = false, error = failed.javaClass.simpleName)
                        }
                    }
                    io.send(reply)
                }
            }

            "shutdown" -> return
            else -> io.log("unknown message: ${message.type()}")
        }
    }
}

/** `--pair-backend <url>`; a missing or flag-looking value is ignored. */
internal fun pairBackendUrl(args: Array<String>): String? {
    val index = args.indexOf("--pair-backend")
    if (index < 0) return null
    return args.getOrNull(index + 1)?.takeIf { it.isNotBlank() && !it.startsWith("--") }
}

/**
 * Builds and starts the gateway from a stored session. Both the boot path and
 * the `backend.pair` command go through this one helper, so refresh, diag state
 * and the turn adapter cannot drift between them.
 */
private class GatewayHost(
    private val io: Stdio,
    private val store: ChatStore,
    private val records: RecordStore,
    private val sessionStore: BackendSessionStore,
    private val runner: EngineTaskRunner,
    private val scope: CoroutineScope,
) {
    private var current: CoroutineScope? = null

    @Synchronized
    fun start(session: BackendSession, contentKey: ByteArray) {
        require(contentKey.size == CONTENT_KEY_BYTES) { "content key must be $CONTENT_KEY_BYTES bytes" }
        current?.cancel()
        var credentials = BackendCredentials(
            deviceId = session.deviceId,
            householdId = session.householdId,
            accessToken = session.accessToken,
            refreshToken = session.refreshToken,
            expiresAt = session.expiresAt,
            gatewayUrl = session.gatewayUrl,
        )
        val pairing = CorePairing(session.gatewayUrl)
        val link: BackendLink = BackendClient(
            credentials = { credentials },
            pushToken = { null },
            refresh = {
                // A refresh rotates the token pair but not the identity, so the
                // stored device and household are copied back in. An unreachable
                // backend is left as a throw for the client's own backoff; only
                // a successful rotation is persisted.
                val refreshed = pairing.refresh(credentials.refreshToken)
                credentials = refreshed.copy(
                    deviceId = session.deviceId,
                    householdId = session.householdId,
                    gatewayUrl = session.gatewayUrl,
                )
                runCatching {
                    sessionStore.save(
                        session.copy(
                            accessToken = refreshed.accessToken,
                            refreshToken = refreshed.refreshToken,
                            expiresAt = refreshed.expiresAt,
                        ),
                    )
                }.onFailure { failed ->
                    io.log("backend session could not be saved (${failed.javaClass.simpleName}); reconnecting with memory only")
                }
                credentials
            },
            deviceName = "Windows PC",
            appVersion = Protocol.VERSION.toString(),
        )
        val gateway = BackendGateway(
            link = link,
            contentKey = contentKey,
            run = runner::run,
            onDiagState = {
                buildJsonObject {
                    put("appVersion", Protocol.VERSION.toString())
                    put("conversations", store.list(includeArchived = true).size)
                    put("runs", records.count())
                }
            },
        )
        val live = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
        current = live
        live.launch { gateway.runForever(live) }
    }

    private companion object {
        const val CONTENT_KEY_BYTES = 32
    }
}

private const val CONTENT_KEY_BYTES = 32

/** Decodes the stored content key; null means the file cannot be used. */
internal fun backendContentKey(session: BackendSession): ByteArray? =
    runCatching { Base64.getDecoder().decode(session.contentKeyBase64) }
        .getOrNull()
        ?.takeIf { it.size == CONTENT_KEY_BYTES }
