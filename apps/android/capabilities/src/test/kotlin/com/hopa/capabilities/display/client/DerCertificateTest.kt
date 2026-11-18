package com.hopa.capabilities.display.client

import java.security.KeyPairGenerator
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DerCertificateTest {
    @Test
    fun the_certificate_parses_and_carries_the_key() {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val pair = generator.generateKeyPair()

        val certificate = DerCertificate().selfSigned(pair)

        assertTrue(
            certificate.subjectX500Principal.name.contains("CN=HOPA"),
            "subject is ${certificate.subjectX500Principal.name}",
        )
        assertTrue(certificate.publicKey == pair.public, "the certificate must be signed by the same key")
        certificate.verify(pair.public)
    }
}
