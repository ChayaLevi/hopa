package com.hopa.agent.loop

import com.hopa.agent.action.ActionExecutor
import com.hopa.agent.classify.ActionKindRules
import com.hopa.agent.decision.DecisionRuntime
import com.hopa.agent.decision.StepPolicy
import com.hopa.agent.decision.model.DecisionParser
import com.hopa.agent.decision.model.ModelReplyText
import com.hopa.agent.prompt.AppCatalogBlock
import com.hopa.agent.prompt.ContextBudget
import com.hopa.agent.prompt.InstalledAppsBlock
import com.hopa.agent.prompt.ScreenRenderer
import com.hopa.agent.prompt.SystemPromptBuilder
import com.hopa.catalog.routing.AppRouter
import com.hopa.catalog.routing.CatalogRoutes
import com.hopa.catalog.routing.RouterHint
import com.hopa.catalog.store.CatalogSnapshot
import com.hopa.contracts.error.ErrorCodes
import com.hopa.contracts.error.HopaError
import com.hopa.contracts.model.ModelMessage
import com.hopa.contracts.model.ModelRequest
import com.hopa.contracts.model.ModelUsage
import com.hopa.contracts.ports.Clock
import com.hopa.contracts.ports.DevicePort
import com.hopa.contracts.turn.TurnContext
import com.hopa.contracts.turn.TurnResult
import com.hopa.contracts.wire.Observation
import com.hopa.contracts.wire.ObservationNode
import com.hopa.contracts.wire.RunRecord
import com.hopa.contracts.wire.ScreenSize
import com.hopa.decision.ActionClass
import com.hopa.decision.Verb
import com.hopa.runlog.store.RunRecorder

class AgentLoop(
    private val stepPolicy: StepPolicy,
    private val device: DevicePort,
    private val executor: ActionExecutor,
    private val recorder: RunRecorder,
    private val clock: Clock,
    private val parser: DecisionParser = DecisionParser(),
    private val promptBuilder: SystemPromptBuilder = SystemPromptBuilder(),
    private val budget: ContextBudget = ContextBudget(),
    private val screenRenderer: ScreenRenderer = ScreenRenderer(),
    private val config: LoopConfig = LoopConfig(),
    private val webAvailable: Boolean = false,
    /** The tables this turn routes and prompts with. */
    private val knowledge: CatalogSnapshot,
    /**
     * The decision layers. In shadow mode they vote into the run log and the
     * model still decides; nothing here changes what executes.
     */
    private val decisions: DecisionRuntime? = null,
) {
    suspend fun run(
        runId: String,
        traceId: String,
        context: TurnContext,
        isCancelled: () -> Boolean = { false },
    ): TurnResult {
        var observation = runCatching { device.observe() }.getOrNull()
        val surfaceAvailable = observation != null
        val targets = CatalogRoutes.targets(context.installedApps, context.platform, knowledge)
        val route = AppRouter.route(context.goal, targets)
        val known = targets
            .filter { it.bundle.isNotBlank() }
            .map { it.bundle.lowercase() }
            .toSet()
        val names = targets
            .filter { it.bundle.isNotBlank() }
            .associate { it.bundle.lowercase() to it.display }
        val installed = if (
            context.platform.equals("android", ignoreCase = true) ||
            context.installedApps.isNotEmpty()
        ) {
            InstalledAppsBlock().text(context.installedApps, known)
        } else {
            ""
        }
        val system = promptBuilder.build(
            memory = context.memory,
            skills = context.skills,
            apps = listOf(
                AppCatalogBlock.text(targets),
                installed,
                typedFunctions(context.appFunctions),
            ).filter { it.isNotEmpty() }.joinToString("\n\n"),
            routerHint = RouterHint.text(route, webAvailable),
            surfaceAvailable = surfaceAvailable,
            webAvailable = webAvailable,
            phoneAccess = context.phoneAccess,
            vision = context.vision,
        )
        val messages = mutableListOf(ModelMessage("system", system))
        messages += budget.fit(context.history)
        messages += ModelMessage("user", context.goal)
        if (observation != null) {
            messages += ModelMessage("user", screenRenderer.render(observation))
        }

        val records = mutableListOf<RunRecord>()
        val narration = mutableListOf<String>()
        val steps = mutableListOf<String>()
        val launched = mutableSetOf<String>()
        var usage = ModelUsage()

        repeat(config.maxSteps) { step ->
            if (isCancelled()) {
                return TurnResult("Stopped.", records, narration, usage = usage)
            }
            val frames = if (context.vision) {
                runCatching { device.screenshot() }.getOrNull()?.let { bytes ->
                    listOf(java.util.Base64.getEncoder().encodeToString(bytes))
                }.orEmpty()
            } else {
                emptyList()
            }
            val reply = runCatching { stepPolicy.next(ModelRequest(messages, images = frames)) }
                .getOrElse { error ->
                    return TurnResult(
                        answer = "I could not reach the model: ${error.message ?: "unknown error"}. " +
                            "Check the provider and network.",
                        records = records,
                        narration = narration,
                        error = HopaError(ErrorCodes.MODEL, error.message ?: "model failed"),
                        usage = usage,
                    )
                }
            usage = usage.plus(reply.usage)
            messages += ModelMessage("assistant", reply.text)

            val outcome = parser.parse(reply.text)
            outcome.error?.let { failure ->
                return TurnResult(
                    answer = "The model sent an invalid step. ${failure.message}",
                    records = records,
                    narration = narration,
                    error = failure,
                    usage = usage,
                )
            }
            outcome.text?.let { text ->
                return TurnResult(ModelReplyText.clean(text), records, narration, usage = usage)
            }
            val decision = outcome.decision
                ?: return TurnResult("I did not understand the answer.", records, narration, usage = usage)
            if (decision.done) {
                return TurnResult(
                    answer = ModelReplyText.clean(decision.say.ifBlank { "Done." }),
                    records = records,
                    narration = narration,
                    usage = usage,
                )
            }
            if (decision.needUser) {
                return TurnResult(
                    answer = ModelReplyText.clean(decision.say),
                    records = records,
                    narration = narration,
                    options = decision.options.orEmpty(),
                    usage = usage,
                )
            }
            val action = decision.action
                ?: return TurnResult(
                    ModelReplyText.clean(decision.say.ifBlank { "Done." }),
                    records,
                    narration,
                    usage = usage,
                )

            // Every active layer votes here. The selected one applies only for
            // the classes the runtime allows, and only to change the step, never
            // the approval: a payment still goes through the gate.
            val run = decisions?.observe(
                runId = runId,
                step = step,
                action = action,
                screen = observation?.let(screenRenderer::render).orEmpty(),
                history = steps.toList(),
            )
            val chosen = run?.decision
            if (chosen != null && decisions.appliesTo(ActionClass.of(action.kindOrNull()))) {
                when (chosen.verb) {
                    Verb.ACT -> Unit

                    Verb.VERIFY -> {
                        observation = runCatching { device.observe() }.getOrNull() ?: observation
                        observation?.let {
                            messages += ModelMessage("user", "LOOK AGAIN\n" + screenRenderer.render(it))
                        }
                        return@repeat
                    }

                    Verb.WAIT -> return TurnResult(
                        answer = "I am waiting: ${chosen.trace}",
                        records = records,
                        narration = narration,
                        usage = usage,
                    )

                    Verb.ASK -> return TurnResult(
                        answer = "I need your go-ahead before this step. ${chosen.trace}",
                        records = records,
                        narration = narration,
                        usage = usage,
                    )

                    Verb.ESCALATE -> return TurnResult(
                        answer = "I need you to take over: ${chosen.trace}",
                        records = records,
                        narration = narration,
                        error = HopaError(ErrorCodes.BLOCKED, chosen.reason),
                        usage = usage,
                    )
                }
            }

            if (action.kind == "ui.launch") {
                val app = action.app.orEmpty()
                if (app.isNotEmpty() && !launched.add(app)) {
                    messages += ModelMessage(
                        "user",
                        "The app $app is already in the foreground. Do not launch it again; " +
                            "read SCREEN TREE and continue.",
                    )
                    return@repeat
                }
            }

            val signature = "${action.kind}|${action.ref}|${action.text}|${action.app}|${action.x},${action.y}"
            if (action.kindOrNull() !in ActionKindRules.READ_ONLY) {
                steps += signature
            }
            if (steps.count { it == signature } >= 3) {
                // Tapping the same field forever is a failure, not progress.
                return TurnResult(
                    answer = "I kept repeating the same step, so I stopped. " +
                        "The screen did not react the way I expected.",
                    records = records,
                    narration = narration,
                    error = HopaError(ErrorCodes.BLOCKED, "repeated action: ${action.kind}"),
                    usage = usage,
                )
            }
            if (isCancelled()) {
                return TurnResult("Stopped.", records, narration, usage = usage)
            }
            val before = observation ?: emptyObservation()
            val result = executor.execute(
                runId = runId,
                step = step,
                traceId = traceId,
                action = action,
                before = before,
            )
            records += result.record
            narration += RunNarrator.line(action, result.record.executed, result.record.error, names)
            observation = result.observation
            clock.sleep(config.settleMillis)
            messages += ModelMessage("user", screenRenderer.render(result.observation))
            if (result.text.isNotEmpty()) {
                messages += ModelMessage("user", result.text)
            }
        }

        if (isCancelled()) {
            return TurnResult("Stopped.", records, narration, usage = usage)
        }
        messages += ModelMessage(
            "user",
            "You have reached the step limit. Summarize what happened and answer in plain text.",
        )
        val final = runCatching { stepPolicy.next(ModelRequest(messages)) }
        final.getOrNull()?.let { usage = usage.plus(it.usage) }
        return TurnResult(
            answer = final.map { it.text }.getOrElse { "I stopped after ${config.maxSteps} steps." },
            records = records,
            narration = narration,
            error = if (final.isSuccess) null else HopaError(ErrorCodes.TIMEOUT, "step limit"),
            usage = usage,
        )
    }

    private companion object {
    }

    /** A short block for the foreground app's typed functions, when it has any. */
    private fun typedFunctions(raw: String): String {
        val text = raw.trim()
        if (text.isEmpty() || text.startsWith("err")) {
            return ""
        }
        return "TYPED FUNCTIONS OF THE CURRENT APP (call these instead of tapping):\n" +
            text.take(1_500)
    }

    private fun emptyObservation(): Observation = Observation(
        id = "obs-empty",
        timestamp = clock.nowMillis(),
        screenSize = ScreenSize(width = 0, height = 0),
        tree = ObservationNode(type = "root"),
    )
}
