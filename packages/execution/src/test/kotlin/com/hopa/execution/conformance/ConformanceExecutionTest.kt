package com.hopa.execution.conformance

import com.hopa.codec.HopaJson
import com.hopa.contracts.testing.FakeApproval
import com.hopa.contracts.testing.FakeClock
import com.hopa.contracts.testing.FakeDevice
import com.hopa.contracts.wire.Observation
import com.hopa.execution.ActionExecutor
import com.hopa.execution.ApprovalPolicy
import com.hopa.execution.DecisionParser
import com.hopa.records.InMemoryRunRecorder
import com.hopa.records.RunRecordFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The six fixtures executed for real: a fake device does nothing and the
 * approval gateway never answers yes, so the record must show exactly what the
 * fixture declares.
 */
class ConformanceExecutionTest {
    @Test
    fun every_fixture_records_what_it_declares() = runBlocking {
        val document = HopaJson.element.parseToJsonElement(
            resource("/fixtures/conformance.json"),
        ).jsonObject
        for (case in document["cases"]!!.jsonArray) {
            val item = case.jsonObject
            val name = item["name"]!!.jsonPrimitive.content
            val observation = HopaJson.element.decodeFromJsonElement(
                Observation.serializer(),
                item["observation"]!!,
            )
            val outcome = DecisionParser().parse(item["modelReply"]!!.toString())
            val action = outcome.decision?.action
            val expect = item["expect"]!!.jsonObject
            val valid = expect["valid"]!!.jsonPrimitive.boolean
            if (!valid || action == null) {
                assertEquals(false, valid, "$name should be rejected")
                continue
            }
            val clock = FakeClock()
            val recorder = InMemoryRunRecorder()
            val executor = ActionExecutor(
                device = FakeDevice(observation),
                browser = null,
                system = null,
                sandbox = null,
                policy = { ApprovalPolicy() },
                approval = FakeApproval(allow = false),
                recorder = recorder,
                factory = RunRecordFactory(clock),
                clock = clock,
            )
            val result = executor.execute(
                runId = "run-$name",
                step = 0,
                traceId = "trace-$name",
                action = action,
                before = observation,
            )
            assertEquals(expect["executed"]!!.jsonPrimitive.boolean, result.record.executed, "$name executed")
            assertEquals(
                expect["classification"]!!.jsonPrimitive.contentOrNull,
                result.record.classification,
                "$name classification",
            )
            assertEquals(
                expect["needsApproval"]!!.jsonPrimitive.boolean,
                result.record.approved != null,
                "$name approval",
            )
        }
    }

    private fun resource(path: String): String =
        requireNotNull(javaClass.getResource(path)) { "missing $path" }.readText()
}
