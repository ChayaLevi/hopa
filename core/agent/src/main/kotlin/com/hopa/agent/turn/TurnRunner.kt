package com.hopa.agent.turn

import com.hopa.agent.action.ActionExecutor
import com.hopa.agent.decision.DecisionRuntime
import com.hopa.agent.decision.StepPolicy
import com.hopa.agent.goal.GoalVerification
import com.hopa.agent.goal.GoalVerifier
import com.hopa.agent.loop.AgentLoop
import com.hopa.agent.plan.PlanSource
import com.hopa.agent.recipe.Recipe
import com.hopa.agent.recipe.RecipeStore
import com.hopa.agent.recipe.ReplayAnswer
import com.hopa.agent.recipe.Replayer
import com.hopa.catalog.store.CatalogSnapshot
import com.hopa.contracts.error.ErrorCodes
import com.hopa.contracts.error.HopaError
import com.hopa.contracts.model.ModelUsage
import com.hopa.contracts.ports.Clock
import com.hopa.contracts.ports.DevicePort
import com.hopa.contracts.turn.TurnContext
import com.hopa.contracts.turn.TurnResult
import com.hopa.runlog.store.RunRecorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One goal, one turn, one path - for every client.
 *
 * The order is the one the app has always used, now written once:
 *
 *  1. an unapproved goal under plan-first is answered with a plan, nothing runs;
 *  2. a surface that cannot see the screen stops the turn honestly;
 *  3. a stored recipe runs cheaply, and answers from its final screen;
 *  4. otherwise the step loop runs under one deadline for the whole turn;
 *  5. the final screen is checked against the person's checkpoints;
 *  6. a recipe is saved only when the turn was clean, verified and reachable.
 *
 * Clients supply the ports and then decide what to do with the outcome: bubbles,
 * receipts, notifications. This class touches no storage, no Android and no
 * conversation.
 */
class TurnRunner(
    private val steps: StepPolicy,
    private val executorFor: (DevicePort) -> ActionExecutor,
    private val recorder: RunRecorder,
    private val clock: Clock,
    private val recipes: RecipeStore? = null,
    private val replayAnswer: ReplayAnswer? = null,
    private val plans: PlanSource? = null,
    private val verifier: GoalVerifier = GoalVerifier(),
    /** The tables this client uses; read per turn, so an update applies next time. */
    private val knowledge: () -> CatalogSnapshot,
    /** Decision layers; null in a client that has none registered. */
    private val decisions: DecisionRuntime? = null,
    /** How long the screen gets between replayed steps; a test passes a no-op. */
    private val settle: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun run(request: TurnRequest, isCancelled: () -> Boolean = { false }): TurnOutcome {
        val started = clock.nowMillis()
        // Read once per turn: an accepted bundle applies to the next turn, and the
        // receipt must name the one this turn actually routed with.
        val tables = knowledge()

        if (request.planFirst && !request.planApproved) {
            return plan(request, started)
        }
        if (!request.surfaceAvailable) {
            return TurnOutcome(
                source = TurnSource.UNAVAILABLE,
                error = HopaError(
                    ErrorCodes.DEVICE,
                    request.surfaceError.ifBlank { "no screen answered" },
                ),
                surfaceName = request.surfaceName,
                surfaceError = request.surfaceError,
                millis = clock.nowMillis() - started,
            )
        }
        if (request.replay) {
            val recipe = runCatching { recipes?.find(request.goal) }.getOrNull()
            if (recipe != null) {
                replay(recipe, request, started, tables)?.let { return it }
            }
        }
        return fromModel(request, started, isCancelled, tables)
    }

    /** Answers with the steps to approve; the person's yes sends a new request. */
    private suspend fun plan(request: TurnRequest, started: Long): TurnOutcome {
        val steps = plans
            ?.let { source -> runCatching { source.plan(request.goal) }.getOrDefault(emptyList()) }
            .orEmpty()
        return TurnOutcome(
            source = TurnSource.PLAN,
            pendingPlan = steps,
            error = if (steps.isEmpty()) HopaError(ErrorCodes.MODEL, "No plan came back") else null,
            surfaceName = request.surfaceName,
            millis = clock.nowMillis() - started,
        )
    }

    /** Null when the recipe did not run to the end; the caller uses the model. */
    private suspend fun replay(
        recipe: Recipe,
        request: TurnRequest,
        started: Long,
        tables: CatalogSnapshot,
    ): TurnOutcome? {
        val installed = request.installedApps.map { it.packageName }
        val outcome = runCatching {
            Replayer(
                executorFor = executorFor,
                installedApps = { installed },
                settle = settle,
            ).replay(recipe, request.device)
        }.getOrNull() ?: return null
        if (!outcome.trustworthy) {
            return null
        }

        // The recipe knows the taps, not the words: one cheap call reads the
        // final screen, so the person gets an answer and not a step count.
        val spoken = outcome.screen?.let { screen ->
            replayAnswer?.let { answer ->
                runCatching { answer.answer(request.goal, screen) }.getOrNull()
            }
        }
        return TurnOutcome(
            source = TurnSource.RECIPE,
            knowledgeVersion = tables.version,
            answer = spoken?.first ?: "Repeated the ${outcome.done} steps that worked last time.",
            usage = ModelUsage(totalTokens = spoken?.second ?: 0),
            surfaceName = request.surfaceName,
            finalScreen = outcome.screen,
            replaySteps = recipe.steps.mapIndexed { index, action -> "${index + 1}. ${action.kind}" },
            millis = clock.nowMillis() - started,
        )
    }

    private suspend fun fromModel(
        request: TurnRequest,
        started: Long,
        isCancelled: () -> Boolean,
        tables: CatalogSnapshot,
    ): TurnOutcome {
        val loop = AgentLoop(
            stepPolicy = steps,
            device = request.device,
            executor = executorFor(request.device),
            recorder = recorder,
            clock = clock,
            webAvailable = request.webAvailable,
            knowledge = tables,
            decisions = decisions,
        )
        val result = withTimeoutOrNull(request.timeoutMillis) {
            loop.run(
                runId = request.runId,
                traceId = request.traceId,
                context = TurnContext(
                    goal = request.goal,
                    history = request.history,
                    memory = request.memory,
                    skills = request.skills,
                    installedApps = request.installedApps,
                    platform = request.platform,
                    vision = request.vision,
                    appFunctions = request.appFunctions,
                    phoneAccess = request.phoneAccess,
                ),
                isCancelled = isCancelled,
            )
        }

        if (result == null) {
            return TurnOutcome(
                source = TurnSource.MODEL,
                knowledgeVersion = tables.version,
                answer = stoppedAfter(request.timeoutMillis),
                error = HopaError(ErrorCodes.TIMEOUT, "the turn ran out of time"),
                surfaceName = request.surfaceName,
                millis = clock.nowMillis() - started,
            )
        }

        // The loop returns no screen, so a turn with checkpoints reads the final
        // one itself. That is the only extra observation, and only when asked.
        val screen = if (request.checkpoints.isEmpty()) {
            null
        } else {
            runCatching { request.device.observe() }.getOrNull()
        }
        val verification = if (request.checkpoints.isEmpty()) {
            null
        } else {
            verifier.verify(screen, request.checkpoints)
        }

        return TurnOutcome(
            source = TurnSource.MODEL,
            knowledgeVersion = tables.version,
            answer = result.answer,
            records = result.records,
            narration = result.narration,
            options = result.options,
            error = result.error,
            usage = result.usage,
            surfaceName = request.surfaceName,
            checkpoints = verification,
            finalScreen = screen,
            recipeSaved = saveRecipe(request, result, verification),
            millis = clock.nowMillis() - started,
        )
    }

    /**
     * A recipe is worth storing only when the next replay could trust it: the
     * turn finished cleanly, something verified, the person's checkpoints hold,
     * and every step that names a ref recorded what that ref pointed at.
     */
    private suspend fun saveRecipe(
        request: TurnRequest,
        result: TurnResult,
        verification: GoalVerification?,
    ): Boolean {
        val store = recipes ?: return false
        if (result.error != null || result.options.isNotEmpty()) {
            return false
        }
        if (result.records.isEmpty() || result.records.none { it.verified }) {
            return false
        }
        if (verification?.complete == false) {
            return false
        }
        if (result.records.any { !it.action.ref.isNullOrBlank() && it.targetKey.isNullOrBlank() }) {
            return false
        }
        return runCatching {
            store.save(
                goal = request.goal,
                steps = result.records.map { it.action },
                anchors = result.records.map { it.targetKey.orEmpty() },
                screenHash = result.records.first().beforeHash.orEmpty(),
                endHash = result.records.last().afterHash.orEmpty(),
            )
        }.isSuccess
    }

    private fun stoppedAfter(timeoutMillis: Long): String = if (timeoutMillis >= 60_000) {
        "I stopped after ${timeoutMillis / 60_000} minutes."
    } else {
        "I stopped after ${timeoutMillis / 1_000} seconds."
    }
}
