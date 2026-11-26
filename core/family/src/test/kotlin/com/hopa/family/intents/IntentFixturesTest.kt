package com.hopa.family.intents

import com.hopa.family.board.FixtureLogs
import com.hopa.family.scope.MemberViews
import com.hopa.family.time.FamilyClock
import com.hopa.family.time.TimeFixtures
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** `logic-intents.json`: Homemind's intents.ts against this port. */
class IntentFixturesTest {
    private val file = TimeFixtures.file("logic-intents.json")
    private val now = FamilyClock.millis(file.getValue("now").jsonPrimitive.content)
    private val seed = FixtureLogs.seed
    private val out = Json {
        encodeDefaults = false
        explicitNulls = false
    }
    private val stores = file.getValue("stores").jsonArray.map { store(it.jsonObject) }

    private fun JsonObject.text(field: String) = getValue(field).jsonPrimitive.content

    private fun store(o: JsonObject) = IntentStore(
        o.text("id"),
        o.text("name"),
        o["nameZh"]?.jsonPrimitive?.content,
        o.getValue("waiting").jsonArray.map {
            it.jsonPrimitive.content
        },
    )

    private fun view(member: String) = MemberViews.viewFor(seed, member)!!

    private fun ctx(member: String) = IntentPrompts.intentContextFor(view(member), now, stores)

    /** JSON equality where 2 and 2.0 are the same number (JavaScript has one number type). */
    private fun same(a: JsonElement?, b: JsonElement?): Boolean = when {
        a == null || a is JsonNull -> b == null || b is JsonNull
        a is JsonObject -> b is JsonObject && a.keys == b.keys && a.keys.all { same(a[it], b[it]) }
        a is JsonArray -> b is JsonArray && a.size == b.size && a.indices.all { same(a[it], b[it]) }
        a is JsonPrimitive && !a.isString ->
            b is JsonPrimitive && !b.isString &&
                (a.content == b.content || (a.doubleOrNull != null && a.doubleOrNull == b.doubleOrNull))
        else -> a == b
    }

    private fun json(intent: ChatIntent): JsonElement = out.encodeToJsonElement(ChatIntent.serializer(), intent)

    /** Homemind writes `signUp: null` when there is none; absent optional fields stay out. */
    private val withNulls = Json {
        encodeDefaults = false
        explicitNulls = true
    }

    private fun json(extraction: Extraction): JsonElement = withNulls.encodeToJsonElement(
        Extraction.serializer(),
        extraction,
    )

    @Test
    fun `contexts hold what each member may see`() {
        for ((member, want) in file.getValue("contexts").jsonObject) {
            val o = want.jsonObject
            val got = ctx(member)
            assertEquals(o.text("memberId"), got.memberId)
            assertEquals(o.getValue("isParent").jsonPrimitive.boolean, got.isParent)
            assertEquals(o.getValue("now").jsonPrimitive.long, got.nowMs)
            assertEquals(o.text("timezone"), got.timezone)
            assertEquals(o.getValue("memberIds").jsonArray.map { it.jsonPrimitive.content }, got.memberIds)
            assertEquals(o.getValue("itemIds").jsonArray.map { it.jsonPrimitive.content }, got.itemIds, member)
            assertEquals(o.getValue("eventIds").jsonArray.map { it.jsonPrimitive.content }, got.eventIds)
            assertEquals(o.getValue("titles").jsonObject.mapValues { it.value.jsonPrimitive.content }, got.titles)
            assertEquals(o.getValue("storeIds").jsonArray.map { it.jsonPrimitive.content }, got.storeIds)
        }
    }

    @Test
    fun `every reply validates as Homemind's does`() {
        val wrong = file.getValue("validations").jsonArray.map { it.jsonObject }.mapNotNull { case ->
            val result = case.getValue("result").jsonObject
            val got = IntentValidator.validate(case["reply"], ctx(case.text("memberId")))
            val ok = result.getValue("ok").jsonPrimitive.boolean
            val match = when (got) {
                is Validation.Ok -> ok && same(result["value"], json(got.value))
                is Validation.Invalid -> {
                    val problems = result.getValue("problems").jsonArray.map { it.jsonPrimitive.content }
                    !ok && problems == got.problems
                }
            }
            if (match) null else "${case.text("name")}: want $result, got $got"
        }
        assertEquals(emptyList<String>(), wrong)
    }

    @Test
    fun `extractions validate as Homemind's do`() {
        for (case in file.getValue("extractions").jsonArray.map { it.jsonObject }) {
            val result = case.getValue("result").jsonObject
            val got = ExtractionValidator.validate(case["reply"], now, case["sourceText"]?.jsonPrimitive?.content)
            when (got) {
                is Validation.Ok -> assertTrue(same(result["value"], json(got.value)), "${case.text("name")}: $got")
                is Validation.Invalid ->
                    assertEquals(
                        result.getValue("problems").jsonArray.map { it.jsonPrimitive.content },
                        got.problems,
                        case.text("name"),
                    )
            }
            assertEquals(result.getValue("ok").jsonPrimitive.boolean, got is Validation.Ok, case.text("name"))
        }
    }

    @Test
    fun `replies parse and dates read as in JavaScript`() {
        for (case in file.getValue("parse").jsonArray.map { it.jsonObject }) {
            val got = ModelJobs.parseReply(case.text("text"))
            assertEquals(case.getValue("json").jsonPrimitive.boolean, got != null, case.text("text"))
            if (got != null) assertTrue(same(case["value"], got), case.text("text"))
        }
        for (case in file.getValue("dates").jsonArray.map { it.jsonObject }) {
            val want = case["iso"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
            assertEquals(want, JsDate.parse(case.text("text"))?.let(FamilyClock::iso), case.text("text"))
        }
    }

    @Test
    fun `prompts are byte for byte Homemind's`() {
        for (case in file.getValue("prompts").jsonArray.map { it.jsonObject }) {
            val input = case.getValue("input").jsonObject
            val preferences = case.getValue("preferences").jsonArray.map { it.jsonObject }.map { p ->
                PromptPreference(
                    about = p["aboutMemberId"]?.jsonPrimitive?.content,
                    text = p.text("text"),
                    reason = p["reason"]?.jsonPrimitive?.content,
                    strength = p.text("strength"),
                    scope = p["scope"]?.jsonPrimitive?.content,
                )
            }
            val request = IntentPrompts.intentPrompt(
                view = view(input.text("memberId")),
                nowMs = now,
                lang = Lang.entries.single { it.wire == input.text("lang") },
                text = input.text("text"),
                recent = input.getValue(
                    "recent",
                ).jsonArray.map { ChatLine(it.jsonObject.text("from"), it.jsonObject.text("text")) },
                stores = input.getValue("stores").jsonArray.map { store(it.jsonObject) },
                preferences = preferences,
            )
            val want = case.getValue("request").jsonObject
            assertEquals(want.text("system"), request.system, "system for ${input.text("memberId")}")
            assertEquals(want.text("user"), request.user, "user for ${input.text("memberId")}")
            assertEquals(want.text("purpose"), request.purpose)
            assertEquals(want.getValue("maxTokens").jsonPrimitive.long.toInt(), request.maxTokens)
        }
        for (case in file.getValue("extractionPrompts").jsonArray.map { it.jsonObject }) {
            val input = case.getValue("input").jsonObject
            val request = IntentPrompts.extractionPrompt(now, input.text("timezone"), input.text("text"))
            val want = case.getValue("request").jsonObject
            assertEquals(want.text("system"), request.system)
            assertEquals(want.text("user"), request.user)
        }
    }

    @Test
    fun `a model job retries once with what was wrong`() {
        val ctx = ctx("mom")
        for (case in file.getValue("runs").jsonArray.map { it.jsonObject }) {
            val replies = case.getValue("replies").jsonArray.toMutableList()
            val seen = mutableListOf<List<ModelTurn>?>()
            val model = ModelPort { request ->
                seen += request.followUp
                when (val next = replies.removeFirstOrNull()) {
                    null -> ModelResult.Failed(ModelErrorCode.EMPTY, "No reply")
                    is JsonPrimitive -> ModelResult.Ok(next.content)
                    else -> ModelErrorCode.entries.single { it.wire == next.jsonObject.text("code") }
                        .let { ModelResult.Failed(it, "failed: ${it.wire}") }
                }
            }
            val got = ModelJobs.runValidated(
                model,
                ModelRequest("intent", "S", "U", 800),
            ) { IntentValidator.validate(it, ctx) }
            val want = case.getValue("result").jsonObject
            val name = case.text("name")
            assertEquals(want.getValue("attempts").jsonPrimitive.long.toInt(), got.attempts, name)
            when (got) {
                is JobResult.Ok -> assertTrue(
                    want.getValue("ok").jsonPrimitive.boolean && same(want["value"], json(got.value)),
                    name,
                )
                is JobResult.Failed -> {
                    assertEquals(want.text("code"), got.code, name)
                    assertEquals(want.text("message"), got.message, name)
                    assertEquals(want["problems"]?.jsonArray?.map { it.jsonPrimitive.content }, got.problems, name)
                }
            }
            val wantTurns = case.getValue("requests").jsonArray.map { turns ->
                (turns as? JsonArray)?.map { ModelTurn(it.jsonObject.text("role"), it.jsonObject.text("content")) }
            }
            assertEquals(wantTurns, seen, name)
        }
    }
}
