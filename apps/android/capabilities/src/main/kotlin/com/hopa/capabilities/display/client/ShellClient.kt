package com.hopa.capabilities.display.client

import com.hopa.capabilities.display.DisplayProtocol
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * The app half: a thin synchronous client for [ShellMain]. Every call returns the
 * helper's own words, so a failure is reported with its reason instead of a
 * silent no-op.
 *
 * It talks to 127.0.0.1 and proves itself with the token from [DisplayToken]
 * before anything else, because a socket owned by the shell cannot be reached
 * from an app and loopback needs a guard of its own.
 */
class ShellClient(
    private val token: String = "",
) {
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private val protocol = DisplayProtocol()

    val connected: Boolean
        get() = socket?.isConnected == true

    fun connect(): Boolean {
        if (connected) {
            // A socket can look connected after the helper died; one round trip
            // tells the truth, and a stale client then reconnects instead of
            // driving a dead helper.
            if (send(protocol.ping()).first) {
                return true
            }
            close()
        }
        return runCatching {
            val created = Socket()
            created.connect(
                InetSocketAddress(InetAddress.getLoopbackAddress(), DisplayProtocol.PORT),
                CONNECT_TIMEOUT_MS,
            )
            created.soTimeout = READ_TIMEOUT_MS
            socket = created
            input = created.getInputStream()
            output = created.getOutputStream()
            reader = BufferedReader(InputStreamReader(created.getInputStream()))
            if (!send(protocol.auth(token)).first) {
                close()
                return false
            }
            send("ping").first
        }.onFailure { error ->
            // The reason matters: "off" without a cause is a dead end.
            android.util.Log.w("HopaShell", "connect failed: ${error.message}", error)
        }.getOrDefault(false)
    }

    fun display(width: Int, height: Int, dpi: Int): Pair<Boolean, String> =
        send(protocol.display(width, height, dpi))

    fun launch(packageName: String): Pair<Boolean, String> = send(protocol.launch(packageName))

    fun tap(x: Int, y: Int): Pair<Boolean, String> = send(protocol.tap(x, y))

    fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, millis: Int): Pair<Boolean, String> =
        send(protocol.swipe(fromX, fromY, toX, toY, millis))

    fun key(code: Int): Pair<Boolean, String> = send(protocol.key(code))

    fun top(): String = send(protocol.top()).second

    /** True only when the helper answered this round trip. */
    fun ping(): Boolean = send(protocol.ping()).first

    fun shot(): ByteArray? {
        val (ok, payload) = send(protocol.shot())
        if (!ok) {
            return null
        }
        val size = payload.toIntOrNull() ?: return null
        val bytes = ByteArray(size)
        var read = 0
        while (read < size) {
            val count = input?.read(bytes, read, size - read) ?: return null
            if (count <= 0) {
                return null
            }
            read += count
        }
        return bytes
    }

    fun appFunctions(packageName: String): Pair<Boolean, String> =
        send(protocol.appFunctions(packageName))

    fun appFunctionCall(
        packageName: String,
        function: String,
        parameters: String,
    ): Pair<Boolean, String> =
        send(protocol.appFunctionCall(packageName, function, parameters.replace(" ", "")))

    fun mute(packageName: String): Pair<Boolean, String> = send(protocol.mute(packageName))

    fun unmute(packageName: String): Pair<Boolean, String> = send(protocol.unmute(packageName))

    fun finish(packages: List<String>): Pair<Boolean, String> =
        send(protocol.finish(packages))

    fun release() {
        runCatching { send(protocol.release()) }
    }

    fun close() {
        runCatching { socket?.close() }
        socket = null
        reader = null
        input = null
        output = null
    }

    @Synchronized
    private fun send(command: String): Pair<Boolean, String> {
        val writer = output ?: return false to "helper is not connected"
        return runCatching {
            writer.write((command + "\n").toByteArray())
            writer.flush()
            val line = reader?.readLine() ?: return false to "helper closed the socket"
            protocol.parse(line)
        }.onFailure { error ->
            android.util.Log.w("HopaShell", "command failed: $command", error)
        }.getOrElse { false to (it.message ?: "${it.javaClass.simpleName}: no reply") }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 1_500
        const val READ_TIMEOUT_MS = 30_000
    }
}
