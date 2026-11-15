package com.hopa.capabilities.display

import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * The app half: a thin synchronous client for [ShellMain]. Every call returns the
 * helper's own words, so a failure is reported with its reason instead of a
 * silent no-op.
 */
class ShellClient {
    private var socket: LocalSocket? = null
    private var reader: BufferedReader? = null
    private var input: InputStream? = null
    private var output: java.io.OutputStream? = null
    private val protocol = DisplayProtocol()

    val connected: Boolean
        get() = socket?.isConnected == true

    fun connect(): Boolean {
        if (connected) {
            return true
        }
        return runCatching {
            val created = LocalSocket()
            created.connect(LocalSocketAddress(SOCKET, LocalSocketAddress.Namespace.ABSTRACT))
            socket = created
            input = created.inputStream
            output = created.outputStream
            reader = BufferedReader(InputStreamReader(created.inputStream))
            send("ping").first
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
        }.getOrElse { false to (it.message ?: "helper failed") }
    }

    private companion object {
        const val SOCKET = "hopa-display"
    }
}
