package com.hopa.sidecar

import com.hopa.sidecar.Protocol.asObject
import com.hopa.sidecar.Protocol.text
import com.hopa.sidecar.Protocol.type
import com.hopa.backend.link.BackendClient
import com.hopa.backend.pairing.BackendCredentials
import com.hopa.backend.pairing.BackendPairing as CorePairing
import com.hopa.contracts.chat.Attachment
import com.hopa.contracts.json.HopaJson
import java.nio.file.Path
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

fun main() {
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
    val sessionStore = BackendSessionStore(dataDir.resolve("backend-session.json"))
    val session = sessionStore.load()
    if (session == null) {
        io.log("backend admission: none; encrypted tasks stay off until a phone pairs this PC")
    } else {
        val contentKey = runCatching { Base64.getDecoder().decode(session.contentKeyBase64) }.getOrNull()
        if (contentKey == null || contentKey.size != 32) {
            io.log("backend admission: found device ${session.deviceId}, but its content key is invalid; encrypted tasks stay off")
        } else {
            io.log("backend admission: found device ${session.deviceId} in household ${session.householdId}")
            var credentials = BackendCredentials(
                deviceId = session.deviceId,
                householdId = session.householdId,
                accessToken = session.accessToken,
                refreshToken = session.refreshToken,
                expiresAt = session.expiresAt,
                gatewayUrl = session.gatewayUrl,
            )
            val pairing = CorePairing(session.gatewayUrl)
            val link = BackendClient(
                credentials = { credentials },
                pushToken = { null },
                refresh = {
                    // A refresh rotates the token pair but not the identity, so
                    // the stored device and household are copied back in. An
                    // unreachable backend is left as a throw for the client's
                    // own backoff; only a successful rotation is persisted.
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
                    }
                    credentials
                },
                deviceName = "Windows PC",
                appVersion = Protocol.VERSION.toString(),
            )
            val runner = EngineTaskRunner(
                startTurn = { id, goal -> engine.startTurn(id, goal, conversationId = null) },
                cancelTurn = engine::cancel,
            )
            engine.onTurnEnd = runner::complete
            val gateway = BackendGateway(link = link, contentKey = contentKey, run = runner::run)
            backendScope.launch { gateway.runForever(backendScope) }
        }
    }
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

            "turn.cancel" -> engine.cancel()

            "device.result" -> device.resolve(message.text("id"), message)
            "approval.result" -> approval.resolve(message.text("id"), message.text("allow").toBoolean())
            "shutdown" -> return
            else -> io.log("unknown message: ${message.type()}")
        }
    }
}
