package com.hopa.capabilities.display

import android.content.Context
import android.os.Build
import android.util.Base64
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.io.File
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.security.KeyFactory
import java.util.concurrent.TimeUnit

/**
 * Wireless debugging without a PC: pair with the phone's own adbd using the code
 * Android shows, then run the display helper as shell. This is the only way to
 * get shell privileges with no companion machine, and the user grants it once.
 */
class WirelessShell(
    private val context: Context,
) : AutoCloseable {
    private var manager: AbsAdbConnectionManager? = null

    val connected: Boolean
        get() = manager?.isConnected == true

    /** Pair with the pairing port Android shows under "Pair device with pairing code". */
    fun pair(host: String, port: Int, code: String): String {
        val manager = manager()
        return runCatching {
            manager.pair(host.trim(), port, code.trim())
            "Paired. Now open Wireless debugging again so the connect port is live."
        }.getOrElse { "Pairing failed: ${it.message}" }
    }

    /** Connect to the TLS port adbd advertises, then start the helper as shell. */
    fun connectAndStartHelper(token: String): String {
        val manager = manager()
        runCatching {
            if (!manager.isConnected) {
                manager.connectTls(context, 15_000)
            }
        }.onFailure { error ->
            return when (error) {
                is io.github.muntashirakon.adb.AdbPairingRequiredException ->
                    "Not paired yet: pair with the code from Wireless debugging first"
                else -> "Could not connect: ${error.message}"
            }
        }
        if (!manager.isConnected) {
            return "adbd did not accept the connection; is Wireless debugging on?"
        }
        val started = shell(DisplayStart.command(context.packageName, token))
        return if (started.contains("started")) {
            "Wireless shell connected; the background screen is ready"
        } else {
            "Helper did not start: ${started.take(120)}"
        }
    }

    fun shell(command: String, timeoutMillis: Long = 15_000): String {
        val manager = manager ?: return "not connected"
        return runCatching {
            manager.openStream("shell:$command").use { stream ->
                val deadline = System.currentTimeMillis() + timeoutMillis
                val out = StringBuilder()
                stream.openInputStream().bufferedReader().useLines { lines ->
                    for (line in lines) {
                        out.appendLine(line)
                        if (System.currentTimeMillis() > deadline) {
                            break
                        }
                    }
                }
                out.toString()
            }
        }.getOrElse { "shell failed: ${it.message}" }
    }

    override fun close() {
        runCatching { manager?.close() }
        manager = null
    }

    private fun manager(): AbsAdbConnectionManager {
        manager?.let { return it }
        val keys = loadOrCreateKeys()
        val created = object : AbsAdbConnectionManager() {
            override fun getPrivateKey(): PrivateKey = keys.first

            override fun getCertificate(): Certificate = keys.second

            override fun getDeviceName(): String = "HOPA"
        }
        created.setApi(Build.VERSION.SDK_INT)
        created.setTimeout(20, TimeUnit.SECONDS)
        manager = created
        return created
    }

    /** One RSA key and one self-signed certificate, kept in app-private storage. */
    private fun loadOrCreateKeys(): Pair<PrivateKey, Certificate> {
        val privateFile = File(context.filesDir, "adbkey.pk8")
        val certificateFile = File(context.filesDir, "adbkey.crt")
        if (privateFile.exists() && certificateFile.exists()) {
            val loaded = runCatching {
                val key = KeyFactory.getInstance("RSA").generatePrivate(
                    PKCS8EncodedKeySpec(privateFile.readBytes()),
                )
                val certificate = CertificateFactory.getInstance("X.509")
                    .generateCertificate(certificateFile.inputStream())
                key to certificate
            }
            if (loaded.isSuccess) {
                return loaded.getOrThrow()
            }
        }

        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048, SecureRandom.getInstance("SHA1PRNG"))
        val pair = generator.generateKeyPair()
        val certificate = DerCertificate().selfSigned(pair)

        runCatching {
            privateFile.writeBytes(pair.private.encoded)
            certificateFile.writeBytes(certificate.encoded)
        }
        return pair.private to certificate
    }
}
