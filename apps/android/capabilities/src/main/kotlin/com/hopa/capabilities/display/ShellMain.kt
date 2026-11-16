package com.hopa.capabilities.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.net.LocalServerSocket
import android.net.LocalSocket
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * The shell half of the background display. Started as uid 2000 with
 * `app_process` (CLASSPATH points at the APK), it owns the virtual display and
 * the input injection, and answers the app over an abstract local socket.
 */
object ShellMain {
    private const val SOCKET = "hopa-display"

    @JvmStatic
    fun main(args: Array<String>) {
        val socket = runCatching { LocalServerSocket(SOCKET) }.getOrElse {
            System.err.println("hopa-display: cannot listen: ${it.message}")
            return
        }
        println("hopa-display: listening")
        while (true) {
            val client = runCatching { socket.accept() }.getOrNull() ?: continue
            // One connection owns one display, so several clients can run several
            // tasks at the same time on separate hidden screens.
            Thread {
                runCatching { serve(client) }
                runCatching { client.close() }
            }.apply { isDaemon = true }.start()
        }
    }

    private fun serve(socket: LocalSocket) {
        val reader = BufferedReader(InputStreamReader(socket.inputStream))
        val out = socket.outputStream
        var host: DisplayHost? = null
        var input: DisplayInput? = null
        var launcher: DisplayLauncher? = null
        val context = systemContext()

        fun reply(line: String) {
            out.write((line + "\n").toByteArray())
            out.flush()
        }

        while (true) {
            val line = reader.readLine() ?: return
            val parts = line.trim().split(' ').filter { it.isNotEmpty() }
            if (parts.isEmpty()) {
                continue
            }
            when (parts[0]) {
                "display" -> {
                    if (parts.size < 4) {
                        reply("err display needs width height dpi")
                        continue
                    }
                    host?.release()
                    val manager = context?.getSystemService(DisplayManager::class.java)
                    if (manager == null) {
                        reply("err no display manager in the helper")
                        continue
                    }
                    val created = runCatching {
                        DisplayHost(manager, parts[1].toInt(), parts[2].toInt(), parts[3].toInt())
                    }
                    if (created.isFailure) {
                        host = null
                        input = null
                        launcher = null
                        reply("err ${created.exceptionOrNull()?.message ?: "createDisplay failed"}")
                        continue
                    }
                    host = created.getOrThrow()
                    input = DisplayInput(host!!.displayId)
                    launcher = DisplayLauncher(host!!.displayId)
                    reply("ok ${host!!.displayId}")
                }

                "launch" -> reply(
                    if (parts.size < 2) {
                        "err launch needs a package"
                    } else {
                        runCatching { launcher!!.launch(parts[1]) }
                            .getOrElse { "err ${it.message}" }
                    },
                )

                "tap" -> reply(
                    if (input != null && parts.size >= 3) {
                        if (input!!.tap(parts[1].toInt(), parts[2].toInt())) "ok" else "err tap refused"
                    } else {
                        "err no display"
                    },
                )

                "swipe" -> reply(
                    if (input != null && parts.size >= 6) {
                        val sent = input!!.swipe(
                            parts[1].toInt(),
                            parts[2].toInt(),
                            parts[3].toInt(),
                            parts[4].toInt(),
                            parts[5].toInt(),
                        )
                        if (sent) "ok" else "err swipe refused"
                    } else {
                        "err no display"
                    },
                )

                "key" -> reply(
                    if (input != null && parts.size >= 2) {
                        if (input!!.key(parts[1].toInt())) "ok" else "err key refused"
                    } else {
                        "err no display"
                    },
                )

                "top" -> reply("ok ${runCatching { launcher?.topPackage() }.getOrNull().orEmpty()}")

                "shot" -> {
                    val bytes = runCatching { host?.frameJpeg() }.getOrNull()
                    if (bytes == null) {
                        reply("err no frame yet")
                    } else {
                        reply("ok ${bytes.size}")
                        out.write(bytes)
                        out.flush()
                    }
                }

                "mute", "unmute" -> {
                    val packageName = parts.getOrNull(1)
                    if (packageName == null || !packageName.matches(Regex("[A-Za-z][A-Za-z0-9_.]*"))) {
                        reply("err mute needs a package")
                        continue
                    }
                    val mode = if (parts[0] == "mute") "ignore" else "default"
                    val output = runCatching {
                        ProcessBuilder("sh", "-c", "appops set $packageName PLAY_AUDIO $mode")
                            .redirectErrorStream(true)
                            .start()
                            .also { it.waitFor() }
                    }
                    if (output.isSuccess) {
                        reply("ok $packageName $mode")
                    } else {
                        reply("err appops refused $packageName")
                    }
                }

                "appfn" -> {
                    val packageName = parts.getOrNull(1)
                    if (packageName == null || !packageName.matches(Regex("[A-Za-z][A-Za-z0-9_.]*"))) {
                        reply("err appfn needs a package")
                        continue
                    }
                    reply("ok " + shellOut(
                        "cmd app_function list-app-functions --package $packageName",
                    ))
                }

                "appfn-call" -> {
                    val packageName = parts.getOrNull(1)
                    val function = parts.getOrNull(2)
                    val parameters = parts.drop(3).joinToString(" ")
                    if (packageName == null || function == null ||
                        !packageName.matches(Regex("[A-Za-z][A-Za-z0-9_.]*"))
                    ) {
                        reply("err appfn-call needs a package and a function")
                        continue
                    }
                    reply("ok " + shellOut(
                        "cmd app_function execute-app-function --package $packageName " +
                            "--function '$function' --parameters '$parameters'",
                    ))
                }

                "finish" -> {
                    val packages = parts.drop(1)
                        .flatMap { it.split(',') }
                        .map { it.trim() }
                        .filter { it.matches(Regex("[A-Za-z][A-Za-z0-9_.]*")) }
                    var closed = 0
                    for (packageName in packages) {
                        val output = runCatching {
                            ProcessBuilder("sh", "-c", "am force-stop $packageName")
                                .redirectErrorStream(true)
                                .start()
                                .also { it.waitFor() }
                        }
                        if (output.isSuccess) {
                            closed += 1
                        }
                    }
                    reply("ok closed $closed")
                }

                "release" -> {
                    host?.release()
                    host = null
                    input = null
                    launcher = null
                    reply("ok")
                }

                "ping" -> reply("ok")
                else -> reply("err unknown command ${parts[0]}")
            }
        }
    }

    /** Run a shell command from the helper and return its output. */
    private fun shellOut(command: String): String = runCatching {
        ProcessBuilder("sh", "-c", command)
            .redirectErrorStream(true)
            .start()
            .also { it.waitFor() }
            .inputStream
            .bufferedReader()
            .readText()
            .trim()
    }.getOrDefault("")

    /** A Context from a bare `app_process`: the system context is enough here. */
    private fun systemContext(): Context? = runCatching {
        val activityThread = Class.forName("android.app.ActivityThread")
        val thread = activityThread.getMethod("systemMain").invoke(null)
        activityThread.getMethod("getSystemContext").invoke(thread) as? Context
    }.getOrNull()
}
