package com.hopa.app.tasks

import com.hopa.app.di.AppContainer
import com.hopa.capabilities.display.ShellClient
import com.hopa.capabilities.display.ShellDisplayDevice
import com.hopa.engine.observation.ScreenSource
import com.hopa.engine.observation.ScreenSourceDevice
import com.hopa.contracts.chat.ChatMessage
import com.hopa.turn.run.TurnSource
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Runs goals side by side, each on its own hidden display. A parallel task never
 * touches the visible screen: two runs on one screen would fight over it, and the
 * person would watch the phone twitch.
 */
class ParallelRunner(
    private val container: AppContainer,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _tasks = MutableStateFlow<List<TaskItem>>(emptyList())
    val tasks: StateFlow<List<TaskItem>> = _tasks.asStateFlow()

    fun canStart(): Boolean = _tasks.value.count { it.state == "running" } < MAX_PARALLEL

    /** Start a goal in its own conversation and hidden display; returns the task id. */
    fun start(goal: String): String {
        val id = "task-" + UUID.randomUUID().toString().take(8)
        _tasks.value = _tasks.value + TaskItem(
            id = id,
            goal = goal,
            conversationId = "",
            state = "running",
        )
        scope.launch { run(id, goal) }
        return id
    }

    fun dismiss(id: String) {
        _tasks.value = _tasks.value.filterNot { it.id == id }
    }

    private suspend fun run(id: String, goal: String) {
        val conversation = runCatching { container.conversations.create() }.getOrNull()
        if (conversation == null) {
            finish(id, "failed", "Could not start a conversation")
            return
        }
        patch(id) { it.copy(conversationId = conversation.id) }

        val started = System.currentTimeMillis()

        val client = ShellClient(container.displayToken.value)
        val device = ShellDisplayDevice(client, container.context)
        val metrics = container.context.resources.displayMetrics
        val opened = runCatching { client.connect() }.getOrDefault(false) &&
            device.start(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
        if (!opened) {
            client.close()
            finish(id, "failed", "The hidden screen is not available")
            return
        }

        try {
            val settings = container.settings.settings.first()
            container.useGate(settings.trustedApps.toList())

            // The hidden display has its own foreground app; its typed functions
            // must come from that display, not from the phone's own screen.
            val functions = runCatching {
                val foreground = device.foreground()
                if (foreground.isBlank()) "" else client.appFunctions(foreground).second
            }.getOrDefault("")

            // The same turn as chat: recipe first, then the loop, then the same
            // rule about saving a recipe. Only the surface differs.
            val shared = ScreenSourceDevice(
                sources = listOf(ScreenSource("background", device, budgetMs = 8_000)),
                fallback = com.hopa.capabilities.device.UnavailableDevicePort(),
            )
            shared.observe()
            val outcome = container.runs.run(
                container.runs.request(
                    goal = goal,
                    device = shared,
                    settings = settings,
                    runId = "run-$id",
                    traceId = UUID.randomUUID().toString(),
                    extraMemory = "A background task. Do not disturb the person's screen.",
                    appFunctions = functions,
                    phoneAccess = settings.accessibilityAllowed,
                ),
            )
            if (outcome.source == TurnSource.UNAVAILABLE) {
                append(
                    conversation.id,
                    "I could not read the hidden screen: " +
                        outcome.surfaceError.take(120).ifBlank { "no screen answered" },
                )
                finish(id, "failed", "The hidden screen did not answer")
                return
            }

            val answer = outcome.answer
            val steps = outcome.replaySteps.ifEmpty {
                outcome.records.map { record ->
                    val target = record.action.app
                        ?: record.action.ref
                        ?: record.action.url
                        ?: record.action.text?.take(40)
                        ?: ""
                    val suffix = if (target.isBlank()) "" else " $target"
                    "${record.step}. ${record.action.kind}$suffix"
                }
            }
            val executed = if (outcome.source == TurnSource.RECIPE) {
                outcome.replaySteps.size
            } else {
                outcome.executed
            }
            val verified = if (outcome.source == TurnSource.RECIPE) executed else outcome.verified
            val proof = com.hopa.contracts.chat.MessageProof(
                path = "background",
                executed = executed,
                verified = verified,
                tokens = outcome.usage.totalTokens,
                millis = System.currentTimeMillis() - started,
                steps = steps,
                knowledgeVersion = outcome.knowledgeVersion,
            )
            append(conversation.id, answer, proof.takeIf { it.executed > 0 })
            record(goal, conversation.id, steps.size, outcome.usage.totalTokens)
            finish(id, "done", answer.take(80))
        } catch (error: Exception) {
            finish(id, "failed", error.message ?: "failed")
        } finally {
            runCatching { device.stop() }
            client.close()
        }
    }

    private suspend fun append(
        conversationId: String,
        text: String,
        proof: com.hopa.contracts.chat.MessageProof? = null,
    ) {
        runCatching {
            container.conversations.append(
                conversationId,
                ChatMessage(
                    id = "m-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}",
                    role = "assistant",
                    text = text,
                    timestamp = System.currentTimeMillis(),
                    proof = proof,
                ),
            )
        }
    }

    private suspend fun record(goal: String, conversationId: String, steps: Int, tokens: Int) {
        runCatching {
            container.runHistory.record(
                at = System.currentTimeMillis(),
                goal = goal,
                path = "background",
                steps = steps,
                tokens = tokens,
                conversationId = conversationId,
            )
        }
    }

    private fun finish(id: String, state: String, detail: String) {
        patch(id) { it.copy(state = state, detail = detail) }
    }

    private fun patch(id: String, transform: (TaskItem) -> TaskItem) {
        _tasks.value = _tasks.value.map { if (it.id == id) transform(it) else it }
    }

    private companion object {
        const val MAX_PARALLEL = 2
    }
}
