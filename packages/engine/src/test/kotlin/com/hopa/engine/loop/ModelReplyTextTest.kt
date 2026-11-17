package com.hopa.engine.loop

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelReplyTextTest {
    @Test
    fun `a safety verdict alone is not an answer`() {
        val raw = "<ds_safety>[用户未成年]否\n[分类]其他\n[判定]正常的应用操作请求\n" +
            "[规则]无</ds_safety>Safe"
        assertEquals(
            "The model stopped at its safety check and did not answer.",
            ModelReplyText.clean(raw),
        )
    }

    @Test
    fun `a verdict before real text keeps the text`() {
        val raw = "<ds_safety>Safe</ds_safety>Connections is open."
        assertEquals("Connections is open.", ModelReplyText.clean(raw))
    }

    @Test
    fun `ordinary answers are untouched`() {
        assertEquals("Wi-Fi is on.", ModelReplyText.clean("Wi-Fi is on."))
    }
}
