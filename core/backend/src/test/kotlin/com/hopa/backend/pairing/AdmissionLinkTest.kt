package com.hopa.backend.pairing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The pending-admission QR is `hopa://pair?v=1&s=<secret>&u=<gateway>`. It is a
 * different type from the operator link (R5): no household id, and an `h`
 * parameter makes it an operator link, not this one.
 */
class AdmissionLinkTest {
    private val link = AdmissionLink(
        session = "s3cr3t-token",
        gatewayUrl = "wss://example.test/v1/gateway",
    )

    @Test
    fun `encode and parse round-trip`() {
        val text = AdmissionLinks.encode(link)
        assertEquals(link, AdmissionLinks.parse(text))
        assertTrue(text.startsWith("hopa://pair?v=1&s="))
    }

    @Test
    fun `parameter order and percent-encoding do not matter`() {
        val order = AdmissionLinks.parse(
            "hopa://pair?u=wss://example.test/v1/gateway&s=s3cr3t-token&v=1",
        )
        val encoded = AdmissionLinks.parse(
            "hopa://pair?v=1&u=wss%3A%2F%2Fexample.test%2Fv1%2Fgateway&s=s3cr3t-token",
        )

        assertEquals(link, order)
        assertEquals(link, encoded)
    }

    @Test
    fun `a version other than one is refused`() {
        assertNull(AdmissionLinks.parse("hopa://pair?v=2&s=s1&u=wss://a/v1/gateway"))
        assertNull(AdmissionLinks.parse("hopa://pair?s=s1&u=wss://a/v1/gateway"))
    }

    @Test
    fun `a missing or blank secret or gateway is refused`() {
        assertNull(AdmissionLinks.parse("hopa://pair?v=1&u=wss://a/v1/gateway"))
        assertNull(AdmissionLinks.parse("hopa://pair?v=1&s=&u=wss://a/v1/gateway"))
        assertNull(AdmissionLinks.parse("hopa://pair?v=1&s=s1"))
        assertNull(AdmissionLinks.parse("hopa://pair?v=1&s=s1&u="))
        assertNull(AdmissionLinks.parse("hello"))
        assertNull(AdmissionLinks.parse(""))
        assertNull(AdmissionLinks.parse("https://example.test/v1/gateway"))
    }

    @Test
    fun `an operator household id is refused`() {
        assertNull(AdmissionLinks.parse("hopa://pair?v=1&h=h1&s=s1&u=wss://a/v1/gateway"))
        assertNull(AdmissionLinks.parse("hopa://pair?v=1&h=&s=s1&u=wss://a/v1/gateway"))
    }
}
