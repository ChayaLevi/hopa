package com.hopa.execution.conformance

import com.hopa.contracts.ObservationHasher
import com.hopa.codec.HopaJson
import com.hopa.contracts.wire.Observation
import com.hopa.execution.ActionClassifier
import com.hopa.execution.ApprovalPolicy
import com.hopa.execution.DecisionParser
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class ConformanceTest {
    @Test
    fun every_fixture_matches_the_contract() {
        val document = resource("/fixtures/conformance.json")
        val cases = document["cases"]!!.jsonArray
        for (case in cases) {
            val item = case.jsonObject
            val name = item["name"]!!.jsonPrimitive.content
            val observation = HopaJson.element.decodeFromJsonElement(
                Observation.serializer(),
                item["observation"]!!,
            )
            val reply = item["modelReply"]!!.toString()
            val expect = item["expect"]!!.jsonObject
            val valid = expect["valid"]!!.jsonPrimitive.boolean
            val outcome = DecisionParser().parse(reply)
            if (!valid) {
                assertNotNull(outcome.error, "$name should be invalid")
                continue
            }
            val action = outcome.decision?.action
            assertNotNull(action, "$name should parse an action")
            action ?: continue
            assertEquals(expect["actionKind"]!!.jsonPrimitive.content, action.kind, "$name action kind")
            val classification = ActionClassifier().classify(action, observation)
            assertEquals(
                expect["classification"]!!.jsonPrimitive.content,
                classification.name.lowercase(),
                "$name classification",
            )
            val needsApproval = ApprovalPolicy().requiresApproval(action, observation)
            assertEquals(expect["needsApproval"]!!.jsonPrimitive.boolean, needsApproval, "$name needsApproval")
            assertEquals(expect["executed"]!!.jsonPrimitive.boolean, !needsApproval, "$name executed")
        }
    }

    @Test
    fun the_observation_hash_vector_matches() {
        val document = resource("/fixtures/observation-hash.json")
        for (case in document["cases"]!!.jsonArray) {
            val item = case.jsonObject
            val observation = HopaJson.element.decodeFromJsonElement(
                Observation.serializer(),
                item["observation"]!!,
            )
            assertEquals(item["hash"]!!.jsonPrimitive.content, ObservationHasher.hash(observation))
        }
    }

    private fun resource(path: String) = HopaJson.element.parseToJsonElement(
        requireNotNull(javaClass.getResource(path)) { "missing $path" }.readText(),
    ).jsonObject
}
