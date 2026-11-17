package com.hopa.capabilities.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Looper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest

/**
 * The shell half of the background display. Started as uid 2000 with
 * `app_process` (CLASSPATH points at a dex that carries this class), it owns the
 * virtual display and the input injection, and answers the app on loopback.
 *
 * Loopback, not an abstract UNIX socket: SELinux denies an app connecting to a
 * socket owned by the shell, and that wall cannot be moved without root. The
 * token from `--token=` is the guard instead; it is created by the app and never
 * leaves the device.
 */
object ShellMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val token = args.firstOrNull { it.startsWith(TOKEN_ARG) }?.substringAfter(TOKEN_ARG).orEmpty()
        val verbose = args.contains(VERBOSE_ARG)
        if (token.length < 16) {
            System.err.println("hopa-display: start it with --token=<token>")
            return
        }
        // ActivityThread.systemMain() builds its handler on the thread's Looper,
        // so one has to exist before the system context is asked for.
        runCatching { Looper.prepareMainLooper() }
        val context = systemContext()
        if (context == null) {
            System.err.println("hopa-display: no system context; cannot reach the display manager")
        }
        val server = runCatching {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(InetAddress.getLoopbackAddress(), DisplayProtocol.PORT))
            }
        }.getOrElse {
            System.err.println("hopa-display: cannot listen: ${it.message}")
            return
        }
        println("hopa-display: listening on 127.0.0.1:${DisplayProtocol.PORT}")
        while (true) {
            val client = runCatching { server.accept() }.getOrNull() ?: continue
            // One connection owns one display, so several clients can run several
            // tasks at the same time on separate hidden screens.
            Thread {
                runCatching { serve(client, token, context, verbose) }
                runCatching { client.close() }
            }.apply { isDaemon = true }.start()
        }
    }

    private fun serve(socket: Socket, token: String, context: Context?, verbose: Boolean) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val out = socket.getOutputStream()
        val hello = reader.readLine() ?: return
        val given = if (hello.startsWith("auth ")) hello.removePrefix("auth ").trim() else ""
        val matches = given.isNotEmpty() &&
            MessageDigest.isEqual(given.toByteArray(), token.toByteArray())
        if (!matches) {
            runCatching {
                out.write("err auth\n".toByteArray())
                out.flush()
            }
            return
        }
        runCatching {
            out.write("ok\n".toByteArray())
            out.flush()
        }
        var host: DisplayHost? = null
        var input: DisplayInput? = null
        var launcher: DisplayLauncher? = null

        fun reply(line: String) {
            out.write((line + "\n").toByteArray())
            out.flush()
        }

        while (true) {
            val line = reader.readLine() ?: return
            if (verbose) {
                System.err.println("cmd: ${line.take(120)}")
                System.err.flush()
            }
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
                    val manager = displayManager(context)
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
    /**
     * The display manager must see the package that owns this uid: the helper
     * runs as shell (2000), so a context for com.android.shell is asked for a
     * manager, otherwise the system refuses with "packageName must match the
     * owner uid".
     */
    private fun displayManager(context: Context?): DisplayManager? {
        if (context == null) {
            return null
        }
        val shell = runCatching {
            context.createPackageContext("com.android.shell", Context.CONTEXT_IGNORE_SECURITY)
        }.getOrNull() ?: context
        return shell.getSystemService(DisplayManager::class.java)
    }

    private const val TOKEN_ARG = "--token="
    /** Command lines are only printed when the caller asked for them. */
    private const val VERBOSE_ARG = "--verbose"

    private fun systemContext(): Context? = runCatching {
        val activityThread = Class.forName("android.app.ActivityThread")
        val thread = activityThread.getMethod("systemMain").invoke(null)
        activityThread.getMethod("getSystemContext").invoke(thread) as? Context
    }.getOrNull()
}
