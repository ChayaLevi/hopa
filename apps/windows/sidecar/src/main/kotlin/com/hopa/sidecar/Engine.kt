package com.hopa.sidecar

import com.hopa.bridge.device.AdbDevice
import com.hopa.bridge.session.BridgeClient
import com.hopa.inference.client.ModelFactory
import com.hopa.agent.action.ActionExecutor
import com.hopa.agent.action.ApprovalPolicy
import com.hopa.agent.loop.AgentLoop
import com.hopa.agent.decision.model.ModelStepPolicy
import com.hopa.contracts.turn.TurnContext
import com.hopa.contracts.model.ModelMessage
import com.hopa.contracts.model.ModelPort
import com.hopa.contracts.ports.DevicePort
import com.hopa.contracts.ports.InstalledApp
import com.hopa.catalog.routing.CatalogRoutes
import com.hopa.contracts.chat.Attachment
import com.hopa.runlog.store.InMemoryRunRecorder
import com.hopa.runlog.record.RunChain
import com.hopa.runlog.record.RunRecordFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One turn at a time; the loop and the gate are the real ones. */
class Engine(
    private val io: Stdio,
    private val hostDevice: HostDevice,
    private val approval: HostApproval,
    private val store: ChatStore,
    private val recordStore: RecordStore,
    private val config: RuntimeConfig,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clock = SystemClock()

    @Volatile
    private var cancelled = false

    /** The turn currently on the engine; cancel(id) must not hit another one. */
    @Volatile
    private var runningTurn: String? = null

    /**
     * Host and backend turns share one loop, one screen and one chat store, so
     * they must not interleave. The gate is held for the whole turn body; a
     * queued turn waits here rather than starting a second loop.
     */
    private val turnGate = Mutex()

    /** Ids waiting for [turnGate]; a cancel before the start is remembered. */
    private val queuedTurns: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val cancelledBeforeStart: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Invoked next to `turn.end` with the turn id and its answer, so the
     * backend gateway can resume a `task.create` (R31). One turn at a time is
     * this engine's contract; the hook must not block.
     */
    @Volatile
    var onTurnEnd: ((id: String, answer: String) -> Unit)? = null

    /** Shutdown/no-id cancel: stops whatever turn is running. */
    @Synchronized
    fun cancel() {
        cancelled = true
        io.log("turn cancelled by the user")
    }

    /** Cancels only the named turn, so a backend task cannot stop a host turn. */
    @Synchronized
    fun cancel(id: String) {
        if (runningTurn == id) {
            cancelled = true
            io.log("turn $id cancelled by the user")
            return
        }
        // The turn is still waiting for the gate. Keep the request so the turn
        // starts already cancelled instead of silently ignoring the user.
        if (queuedTurns.remove(id)) {
            cancelledBeforeStart += id
            io.log("turn $id cancelled before it started")
        }
    }

    @Synchronized
    private fun beginTurn(id: String) {
        queuedTurns.remove(id)
        cancelled = cancelledBeforeStart.remove(id)
        runningTurn = id
    }

    @Synchronized
    private fun finishTurn(id: String) {
        if (runningTurn == id) runningTurn = null
    }

    /**
     * The apps this PC can open, from the catalog's windows column. There is no
     * installed-app inventory here yet, so only entries we know how to start are
     * offered, and the prompt says a launch may still fail.
     */
    private fun windowsApps(): List<InstalledApp> = CatalogRoutes
        .targets(platform = "windows", knowledge = SidecarKnowledge.value)
        .filter { it.kind == "app" && it.bundle.isNotEmpty() }
        .map { InstalledApp(packageName = it.bundle, label = it.display) }

    fun startTurn(
        id: String,
        goal: String,
        conversationId: String?,
        attachment: Attachment? = null,
    ) {
        queuedTurns += id
        scope.launch {
            runSerialized(id) { runTurn(id, goal, conversationId, attachment) }
        }
    }

    /**
     * The one gate every turn goes through. Extracted so the serialization is
     * testable with fake turn bodies: the second body does not run until the
     * first has finished and released the gate.
     */
    internal suspend fun runSerialized(id: String, turn: suspend () -> String) {
        turnGate.withLock {
            beginTurn(id)
            try {
                guardedTurn(id, turn)
            } finally {
                cancelledBeforeStart.remove(id)
            }
        }
    }

    /**
     * Runs one turn and guarantees exactly one `onTurnEnd`, even when the turn
     * throws before it reaches `turn.end`. Extracted so the failure contract is
     * testable without a model or a host.
     */
    internal suspend fun guardedTurn(id: String, turn: suspend () -> String) {
        var ending: String? = null
        try {
            ending = turn()
        } catch (cancelledTurn: CancellationException) {
            ending = CANCELLED_ANSWER
            throw cancelledTurn
        } catch (failed: Exception) {
            // The failure text may quote the goal or model output; log the
            // turn id only and answer generically.
            io.log("turn $id failed before it ended")
            ending = FAILED_ANSWER
        } finally {
            finishTurn(id)
            ending?.let { answer -> notifyTurnEnd(id, answer) }
        }
    }

    /** Runs the real loop and returns its answer; `turn.end` is already sent. */
    private suspend fun runTurn(
        id: String,
        goal: String,
        conversationId: String?,
        attachment: Attachment?,
    ): String {
        val conversation = conversationId?.let { store.get(it) } ?: store.create()
        val history = conversation.messages.map { ModelMessage(it.role, it.text) }
        store.append(conversation.id, "user", goal, attachment)
        // The goal may be private; it is persisted in the chat store, never logged.
        io.log("turn $id in ${conversation.id}")

        val device: DevicePort = if (config.hasBridge) {
            val bridge = BridgeClient(
                baseUrl = config.bridgeUrl,
                token = config.bridgeToken,
                refresh = {
                    val refresh = config.bridgeRefresh
                    if (refresh.isBlank()) {
                        null
                    } else {
                        runCatching { BridgeClient(config.bridgeUrl, "").refreshSession(refresh) }
                            .getOrNull()
                            ?.let { session ->
                                config.setSession(session.token, session.refresh)
                                io.log("bridge token refreshed")
                                io.send(Protocol.session(session.token, session.refresh))
                                session.token
                            }
                    }
                },
            )
            AdbDevice(bridge, clock)
        } else {
            hostDevice
        }
        val model: ModelPort = if (config.hasModel) {
            ModelFactory().create(
                provider = config.modelProvider,
                endpoint = config.modelBaseUrl,
                apiKey = config.modelKey,
                modelName = config.modelName,
            )
        } else {
            SpikeModel()
        }

        val recorder = InMemoryRunRecorder()
        val executor = ActionExecutor(
            device = device,
            browser = null,
            system = null,
            sandbox = null,
            policy = { ApprovalPolicy() },
            approval = approval,
            recorder = recorder,
            factory = RunRecordFactory(clock),
            clock = clock,
        )
        val loop = AgentLoop(
            stepPolicy = ModelStepPolicy(model),
            device = device,
            executor = executor,
            recorder = recorder,
            clock = clock,
            webAvailable = false,
            knowledge = SidecarKnowledge.value,
        )
        val outcome = runCatching {
            loop.run(
                runId = "run-$id",
                traceId = UUID.randomUUID().toString(),
                context = TurnContext(
                    goal = goal,
                    history = history,
                    platform = "windows",
                    installedApps = windowsApps(),
                    phoneAccess = true,
                ),
                isCancelled = { cancelled },
            )
        }

        val answer = outcome.getOrNull()?.answer
            ?: "The sidecar failed: ${outcome.exceptionOrNull()?.message ?: "unknown error"}"
        val records = outcome.getOrNull()?.records.orEmpty()
        recordStore.append(conversation.id, "run-$id", records)
        val chainOk = records.isEmpty() || RunChain.verify(records)
        store.append(conversation.id, "assistant", answer)
        val updated = store.get(conversation.id)
        io.send(
            Protocol.turnEnd(
                id = id,
                conversationId = conversation.id,
                title = updated?.title ?: "New chat",
                answer = answer,
                steps = records,
                chainOk = chainOk,
            ),
        )
        return answer
    }

    private fun notifyTurnEnd(id: String, answer: String) {
        try {
            onTurnEnd?.invoke(id, answer)
        } catch (failed: Exception) {
            // A broken backend hook must not lose a host turn that already ended.
            io.log("turn $id ended but its backend hook failed")
        }
    }

    private companion object {
        const val FAILED_ANSWER = "The sidecar failed before it could finish the turn."
        const val CANCELLED_ANSWER = "The turn was cancelled."
    }
}
