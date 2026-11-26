package com.hopa.family.judgment

import com.hopa.family.time.TimeFixtures
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** `logic-judgment.json`: Homemind's judgeText (JavaScript regex) against this port (Java regex). */
class JudgmentFixturesTest {
    @Test
    fun `every message gets Homemind's tier, reason and quote`() {
        val cases = TimeFixtures.file("logic-judgment.json").getValue("cases").jsonArray.map { it.jsonObject }
        assertTrue(cases.size >= 40)
        val mismatches = cases.mapNotNull { case ->
            val text = case.getValue("text").jsonPrimitive.content
            val want = listOf(
                case.getValue("tier").jsonPrimitive.content,
                case.getValue("reason").jsonPrimitive.content,
                case["quote"]?.jsonPrimitive?.content,
            )
            val judged = Judgment.judge(text)
            val got = listOf(judged.tier.wire, judged.reason.wire, judged.quote)
            if (want == got) null else "${text.replace("\n", "\\n")}: want $want, got $got"
        }
        assertEquals(emptyList<String>(), mismatches)
    }
}
