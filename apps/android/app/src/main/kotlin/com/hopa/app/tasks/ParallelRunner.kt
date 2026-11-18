package com.hopa.app.tasks

import com.hopa.app.di.AppContainer
import com.hopa.capabilities.display.ShellClient
import com.hopa.capabilities.display.ShellDisplayDevice
import com.hopa.engine.loop.TurnContext
import com.hopa.engine.observation.ScreenSource
import com.hopa.engine.observation.ScreenSourceDevice
import com.hopa.stores.ChatMessage
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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

            // A goal that worked before runs from its recipe here too: the
            // parallel run gets the cheap path, not just the main one.
            if (settings.replaySkills) {
                val recipe = runCatching { container.recipes.find(goal) }.getOrNull()
                if (recipe != null) {
                    val outcome = runCatching {
                        com.hopa.app.recipes.Replayer(container).replay(recipe, device)
                    }.getOrNull()
                    if (outcome?.trustworthy == true) {
                        val replayed = outcome.done
                        val answer = "Repeated the $replayed steps that worked last time."
                        val steps = recipe.steps.mapIndexed { index, action ->
                            "${index + 1}. ${action.kind}"
                        }
                        append(
                            conversation.id,
                            answer,
                            com.hopa.stores.MessageProof(
                                path = "background",
                                executed = replayed,
                                verified = replayed,
                                tokens = 0,
                                steps = steps,
                            ),
                        )
                        record(goal, conversation.id, replayed, 0)
                        finish(id, "done", answer.take(80))
                        return
                    }
                }
            }

            // The hidden display has its own foreground app; its typed functions
            // must come from that display, not from the phone's own screen.
            val functions = runCatching {
                val foreground = device.foreground()
                if (foreground.isBlank()) "" else client.appFunctions(foreground).second
            }.getOrDefault("")

            val installed = runCatching { container.packages.installed() }.getOrDefault(emptyList())
            val loop = container.loopFor(
                ScreenSourceDevice(
                    sources = listOf(ScreenSource("background", device, budgetMs = 8_000)),
                    fallback = com.hopa.capabilities.device.UnavailableDevicePort(),
                ),
            )
            val result = withTimeoutOrNull(120_000) {
                loop.run(
                    runId = "run-$id",
                    traceId = UUID.randomUUID().toString(),
                    context = TurnContext(
                        goal = goal,
                        history = emptyList(),
                        memory = listOf(
                            "A background task. Do not disturb the person's screen.",
                            runCatching { container.memory.promptText() }.getOrDefault(""),
                        ).filter { it.isNotBlank() }.joinToString("\n\n"),
                        skills = runCatching { container.skills.promptText() }.getOrDefault(""),
                        vision = settings.vision,
                        installedApps = installed,
                        appFunctions = functions,
                        platform = "android",
                        phoneAccess = settings.accessibilityAllowed,
                    ),
                    isCancelled = { false },
                )
            }
            val answer = result?.answer ?: "Stopped after two minutes."
            val steps = result?.records.orEmpty().map { record ->
                val target = record.action.app
                    ?: record.action.ref
                    ?: record.action.url
                    ?: record.action.text?.take(40)
                    ?: ""
                val suffix = if (target.isBlank()) "" else " $target"
                "${record.step}. ${record.action.kind}$suffix"
            }
            val proof = com.hopa.stores.MessageProof(
                path = "background",
                executed = result?.records?.count { it.executed } ?: 0,
                verified = result?.records?.count { it.verified } ?: 0,
                tokens = result?.usage?.totalTokens ?: 0,
                millis = System.currentTimeMillis() - started,
                steps = steps,
            )
            append(conversation.id, answer, proof.takeIf { it.executed > 0 })
            if (
                result != null &&
                result.error == null &&
                result.options.isEmpty() &&
                result.records.any { it.verified }
            ) {
                runCatching {
                    container.recipes.save(
                        goal = goal,
                        steps = result.records.map { it.action },
                        anchors = result.records.map { it.targetKey.orEmpty() },
                        screenHash = result.records.first().beforeHash.orEmpty(),
                        endHash = result.records.last().afterHash.orEmpty(),
                    )
                }
            }
            record(goal, conversation.id, result?.records?.size ?: 0, result?.usage?.totalTokens ?: 0)
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
        proof: com.hopa.stores.MessageProof? = null,
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
