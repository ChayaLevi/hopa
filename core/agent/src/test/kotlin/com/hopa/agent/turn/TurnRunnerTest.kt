package com.hopa.agent.turn

import com.hopa.agent.action.ActionExecutor
import com.hopa.agent.decision.StepPolicy
import com.hopa.agent.gate.ApprovalPolicy
import com.hopa.agent.goal.Checkpoint
import com.hopa.agent.plan.PlanSource
import com.hopa.agent.recipe.RecipeStore
import com.hopa.catalog.data.ApprovalPolicyData
import com.hopa.catalog.knowledge.Knowledge
import com.hopa.contracts.approval.ApprovalGateway
import com.hopa.contracts.approval.ApprovalRequest
import com.hopa.contracts.error.ErrorCodes
import com.hopa.contracts.model.ModelReply
import com.hopa.contracts.model.ModelRequest
import com.hopa.contracts.model.ModelUsage
import com.hopa.contracts.ports.Clock
import com.hopa.contracts.ports.DevicePort
import com.hopa.contracts.ports.InstalledApp
import com.hopa.contracts.wire.Action
import com.hopa.contracts.wire.Observation
import com.hopa.contracts.wire.ObservationNode
import com.hopa.contracts.wire.ScreenSize
import com.hopa.runlog.store.InMemoryRunRecorder
import com.hopa.runlog.record.RunRecordFactory
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

private const val SETTINGS = "com.android.settings"

class TurnRunnerTest {
    @TempDir
    lateinit var folder: File

    // ── Fakes ─────────────────────────────────────────────────────────────────

    private class FakeClock : Clock {
        override fun nowMillis(): Long = 1_000L

        override suspend fun sleep(millis: Long) = Unit
    }

    private class ScriptedPolicy(private val replies: List<String>) : StepPolicy {
        var calls: Int = 0
            private set

        override suspend fun next(request: ModelRequest): ModelReply {
            check(replies.isNotEmpty()) { "the model must not be called in this test" }
            val text = replies[minOf(calls, replies.lastIndex)]
            calls += 1
            return ModelReply(text, ModelUsage(totalTokens = 5))
        }
    }

    private object AlwaysApprove : ApprovalGateway {
        override suspend fun approve(request: ApprovalRequest): Boolean = true
    }

    private class FakeDevice(screens: List<Observation>) : DevicePort {
        private val screens = screens
        private var index = 0

        var observed: Int = 0
            private set
        val taps = mutableListOf<String>()
        val launches = mutableListOf<String>()

        override suspend fun observe(): Observation {
            observed += 1
            return screens[index.coerceAtMost(screens.lastIndex)]
        }

        override suspend fun tap(ref: String?, x: Int?, y: Int?) {
            taps += ref ?: "$x,$y"
            advance()
        }

        override suspend fun type(text: String, ref: String?) = advance()

        override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, velocity: Int) = advance()

        override suspend fun pressBack() = advance()

        override suspend fun pressHome() = advance()

        override suspend fun launch(packageName: String) {
            launches += packageName
        }

        override suspend fun stop(packageName: String) = Unit

        override suspend fun foreground(): String = SETTINGS

        override suspend fun screenshot(): ByteArray? = null

        override suspend fun apps(): List<InstalledApp> = emptyList()

        private fun advance() {
            if (index < screens.lastIndex) {
                index += 1
            }
        }
    }

    private class Harness(
        screens: List<Observation>,
        replies: List<String> = emptyList(),
        plan: List<String>? = null,
        store: RecipeStore? = null,
    ) {
        val device = FakeDevice(screens)
        val policy = ScriptedPolicy(replies)
        private val knowledge = Knowledge(
            version = "test",
            catalog = emptyList(),
            sites = emptyList(),
            approval = ApprovalPolicyData,
            promptRules = emptyList(),
        )
        private val clock = FakeClock()
        private val recorder = InMemoryRunRecorder()
        private val executor = ActionExecutor(
            device = device,
            browser = null,
            system = null,
            sandbox = null,
            policy = { ApprovalPolicy() },
            approval = AlwaysApprove,
            recorder = recorder,
            factory = RunRecordFactory(clock),
            clock = clock,
        )
        val runner = TurnRunner(
            steps = policy,
            executorFor = { executor },
            recorder = recorder,
            clock = clock,
            recipes = store,
            plans = plan?.let { planned -> PlanSource { planned } },
            knowledge = { knowledge },
            settle = {},
        )

        fun request(
            goal: String,
            planFirst: Boolean = false,
            planApproved: Boolean = false,
            checkpoints: List<Checkpoint> = emptyList(),
            surfaceAvailable: Boolean = true,
            surfaceError: String = "",
        ): TurnRequest = TurnRequest(
            goal = goal,
            device = device,
            runId = "run-1",
            traceId = "trace-1",
            installedApps = listOf(InstalledApp(SETTINGS, "Settings")),
            platform = "android",
            surfaceName = "accessibility",
            checkpoints = checkpoints,
            planFirst = planFirst,
            planApproved = planApproved,
            surfaceAvailable = surfaceAvailable,
            surfaceError = surfaceError,
        )
    }

    private fun labelled(text: String): Observation = observation(
        ObservationNode(
            ref = "e1",
            type = "TextView",
            text = text,
            id = "android:id/title",
        ),
    )

    private fun observation(vararg nodes: ObservationNode): Observation = Observation(
        id = "obs-${nodes.joinToString { it.text }}",
        timestamp = 1L,
        screenSize = ScreenSize(width = 1080, height = 2400),
        tree = ObservationNode(type = "root", children = nodes.toList()),
    )

    private fun store(): RecipeStore = RecipeStore(File(folder, "recipes.txt"))

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun a_plan_comes_back_before_anything_is_tapped() = runBlocking {
        val harness = Harness(
            screens = listOf(labelled("Settings")),
            plan = listOf("Open Settings", "Tap Connections"),
        )

        val outcome = harness.runner.run(harness.request("Open connections", planFirst = true))

        assertEquals(TurnSource.PLAN, outcome.source)
        assertEquals(listOf("Open Settings", "Tap Connections"), outcome.pendingPlan)
        assertEquals(0, harness.device.observed)
        assertNull(outcome.error)
    }

    @Test
    fun a_plan_gate_with_no_source_says_so_instead_of_running() = runBlocking {
        val harness = Harness(screens = listOf(labelled("Settings")))

        val outcome = harness.runner.run(harness.request("Open connections", planFirst = true))

        assertEquals(TurnSource.PLAN, outcome.source)
        assertTrue(outcome.pendingPlan.isEmpty())
        assertEquals(ErrorCodes.MODEL, outcome.error?.code)
        assertEquals(0, harness.policy.calls)
        assertEquals(0, harness.device.observed)
    }

    @Test
    fun an_unavailable_surface_stops_the_turn_and_the_model_is_not_asked() = runBlocking {
        val harness = Harness(screens = listOf(labelled("Settings")))

        val outcome = harness.runner.run(
            harness.request(
                "Turn on Wi-Fi",
                surfaceAvailable = false,
                surfaceError = "accessibility is off",
            ),
        )

        assertEquals(TurnSource.UNAVAILABLE, outcome.source)
        assertEquals(ErrorCodes.DEVICE, outcome.error?.code)
        assertEquals("accessibility is off", outcome.surfaceError)
        assertEquals(0, harness.policy.calls)
        assertEquals(0, harness.device.observed)
    }

    @Test
    fun a_stored_recipe_runs_without_the_model() = runBlocking {
        val store = store()
        store.save(
            goal = "Turn on Wi-Fi",
            steps = listOf(
                Action(kind = "ui.launch", app = SETTINGS),
                Action(kind = "ui.tap", ref = "e1"),
            ),
            anchors = listOf("", "TextView|text=wi-fi|id=android:id/title"),
        )
        val harness = Harness(screens = listOf(labelled("Wi-Fi")), store = store)

        val outcome = harness.runner.run(harness.request("Turn on Wi-Fi"))

        assertEquals(TurnSource.RECIPE, outcome.source)
        assertEquals(2, outcome.replaySteps.size)
        assertEquals(0, harness.policy.calls)
        assertEquals(listOf(SETTINGS), harness.device.launches)
        assertEquals(listOf("e1"), harness.device.taps)
        assertTrue(outcome.answer.startsWith("Repeated the 2 steps"))
    }

    @Test
    fun a_model_turn_is_answered_and_its_steps_are_saved() = runBlocking {
        val store = store()
        val harness = Harness(
            screens = listOf(labelled("Wi-Fi"), labelled("Wi-Fi on")),
            replies = listOf(
                """{"kind":"ui.tap","ref":"e1"}""",
                """{"say":"Wi-Fi is on.","done":true,"needUser":false}""",
            ),
            store = store,
        )

        val outcome = harness.runner.run(harness.request("Turn on Wi-Fi"))

        assertEquals(TurnSource.MODEL, outcome.source)
        assertEquals("Wi-Fi is on.", outcome.answer)
        assertEquals(1, outcome.executed)
        assertEquals(1, outcome.verified)
        assertTrue(outcome.recipeSaved)
        assertEquals("test", outcome.knowledgeVersion)
        val recipe = store.find("Turn on Wi-Fi")
        assertEquals(1, recipe?.steps?.size)
        assertEquals("TextView|text=wi-fi|id=android:id/title", recipe?.anchorAt(0))
    }

    @Test
    fun an_invalid_step_is_reported_and_nothing_is_saved() = runBlocking {
        val store = store()
        val harness = Harness(
            screens = listOf(labelled("Wi-Fi")),
            replies = listOf("""{"kind":"ui.tap"}"""),
            store = store,
        )

        val outcome = harness.runner.run(harness.request("Tap something"))

        assertEquals(ErrorCodes.SCHEMA, outcome.error?.code)
        assertTrue(outcome.records.isEmpty())
        assertFalse(outcome.recipeSaved)
        assertNull(store.find("Tap something"))
    }

    @Test
    fun a_met_checkpoint_lets_the_recipe_be_saved() = runBlocking {
        val store = store()
        val harness = Harness(
            screens = listOf(labelled("Wi-Fi"), labelled("Wi-Fi on")),
            replies = listOf(
                """{"kind":"ui.tap","ref":"e1"}""",
                """{"say":"Wi-Fi is on.","done":true,"needUser":false}""",
            ),
            store = store,
        )

        val outcome = harness.runner.run(
            harness.request("Turn on Wi-Fi", checkpoints = listOf(Checkpoint("Wi-Fi on"))),
        )

        assertTrue(outcome.checkpoints?.complete == true)
        assertTrue(outcome.recipeSaved)
    }

    @Test
    fun a_missing_checkpoint_keeps_the_recipe_out_of_the_store() = runBlocking {
        val store = store()
        val harness = Harness(
            screens = listOf(labelled("Wi-Fi"), labelled("Wi-Fi on")),
            replies = listOf(
                """{"kind":"ui.tap","ref":"e1"}""",
                """{"say":"Wi-Fi is on.","done":true,"needUser":false}""",
            ),
            store = store,
        )

        val outcome = harness.runner.run(
            harness.request("Turn on Wi-Fi", checkpoints = listOf(Checkpoint("Bluetooth"))),
        )

        assertFalse(outcome.checkpoints?.complete == true)
        assertFalse(outcome.recipeSaved)
        assertNull(store.find("Turn on Wi-Fi"))
    }

    @Test
    fun a_tap_on_a_label_less_node_is_not_stored_as_a_recipe() = runBlocking {
        val store = store()
        val harness = Harness(
            screens = listOf(
                observation(
                    ObservationNode(ref = "e1", type = "View"),
                    ObservationNode(ref = "e2", type = "TextView", text = "Settings"),
                ),
                observation(
                    ObservationNode(ref = "e1", type = "View"),
                    ObservationNode(ref = "e2", type = "TextView", text = "Settings opened"),
                ),
            ),
            replies = listOf(
                """{"kind":"ui.tap","ref":"e1"}""",
                """{"say":"Opened.","done":true,"needUser":false}""",
            ),
            store = store,
        )

        val outcome = harness.runner.run(harness.request("Open settings"))

        assertEquals(1, outcome.executed)
        assertFalse(outcome.recipeSaved)
        assertNull(store.find("Open settings"))
    }
}
