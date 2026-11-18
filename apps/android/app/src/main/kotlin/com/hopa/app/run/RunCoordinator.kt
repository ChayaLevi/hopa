package com.hopa.app.run

import com.hopa.app.di.AppContainer
import com.hopa.capabilities.device.UnavailableDevicePort
import com.hopa.contracts.model.ModelMessage
import com.hopa.contracts.ports.DevicePort
import com.hopa.engine.loop.ModelStepPolicy
import com.hopa.engine.observation.ScreenSourceDevice
import com.hopa.stores.AppSettings
import com.hopa.turn.goal.Checkpoint
import com.hopa.turn.plan.ModelPlanSource
import com.hopa.turn.recipe.ReplayAnswer
import com.hopa.turn.run.TurnOutcome
import com.hopa.turn.run.TurnRequest
import com.hopa.turn.run.TurnRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The app's one way into a turn: it knows where the ports are, what the person
 * set, and how to describe this phone. [TurnRunner] knows what a turn is, so the
 * chat screen, a parallel run and a delivered task stop carrying their own copy
 * of plan-first, replay and recipe saving.
 */
class RunCoordinator(
    private val container: AppContainer,
) {
    private val runner by lazy {
        TurnRunner(
            steps = ModelStepPolicy(container.model),
            executorFor = container::executorFor,
            recorder = container.recorder,
            clock = container.clock,
            recipes = container.recipes,
            replayAnswer = ReplayAnswer(container.model),
            plans = ModelPlanSource(container.model),
        )
    }

    /** Everything this client can tell the runner about the screen and the person. */
    suspend fun request(
        goal: String,
        device: ScreenSourceDevice,
        settings: AppSettings,
        runId: String,
        traceId: String,
        history: List<ModelMessage> = emptyList(),
        checkpoints: List<Checkpoint> = emptyList(),
        extraMemory: String = "",
        appFunctions: String? = null,
        phoneAccess: Boolean? = null,
    ): TurnRequest {
        val facts = runCatching { container.memory.promptText() }.getOrDefault("")
        val character = "Character: ${settings.tone} tone, ${settings.chattiness} length."
        val memory = listOf(extraMemory, facts, character)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        return TurnRequest(
            goal = goal,
            device = device,
            runId = runId,
            traceId = traceId,
            history = history,
            memory = memory,
            skills = runCatching { container.skills.promptText() }.getOrDefault(""),
            installedApps = runCatching { container.packages.installed() }.getOrDefault(emptyList()),
            platform = "android",
            vision = settings.vision,
            appFunctions = appFunctions ?: typedFunctions(device),
            phoneAccess = phoneAccess ?: (device.sourceName != "fallback"),
            checkpoints = checkpoints,
            replay = settings.replaySkills,
            surfaceName = device.sourceName,
            surfaceAvailable = device.sourceName != "fallback",
            surfaceError = device.lastError,
        )
    }

    /**
     * The plan gate asks for steps, so it gets no surface and no phone facts: a
     * plan must never open the hidden display or query the helper.
     */
    fun planRequest(goal: String, runId: String, traceId: String): TurnRequest = TurnRequest(
        goal = goal,
        device = ScreenSourceDevice(emptyList(), UnavailableDevicePort()),
        runId = runId,
        traceId = traceId,
        planFirst = true,
    )

    suspend fun run(
        request: TurnRequest,
        isCancelled: () -> Boolean = { false },
    ): TurnOutcome = runner.run(request, isCancelled)

    /**
     * Ask the helper what the foreground app publishes, so the model can choose a
     * typed call over a series of taps. Empty when no shell is available.
     */
    private suspend fun typedFunctions(device: DevicePort): String = withContext(Dispatchers.IO) {
        // The helper is reached over a local socket: never from the main thread.
        if (!container.shell.connected) {
            return@withContext ""
        }
        val foreground = runCatching { device.foreground() }.getOrDefault("")
        if (foreground.isBlank()) {
            return@withContext ""
        }
        runCatching { container.shell.appFunctions(foreground).second }.getOrDefault("")
    }
}
