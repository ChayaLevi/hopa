package com.hopa.capabilities.display

import java.security.KeyPairGenerator
import org.junit.Assert.assertTrue
import org.junit.Test

class DerCertificateTest {
    @Test
    fun the_certificate_parses_and_carries_the_key() {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val pair = generator.generateKeyPair()

        val certificate = DerCertificate().selfSigned(pair)

        assertTrue(
            "subject is ${certificate.subjectX500Principal.name}",
            certificate.subjectX500Principal.name.contains("CN=HOPA"),
        )
        assertTrue(
            "the certificate must be signed by the same key",
            certificate.publicKey == pair.public,
        )
        certificate.verify(pair.public)
    }
}
