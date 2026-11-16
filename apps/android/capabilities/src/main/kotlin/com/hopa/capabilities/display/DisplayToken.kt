package com.hopa.capabilities.display

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * The secret the app and the helper share. The helper listens on loopback, so
 * the token is what stops another app on the phone from driving the hidden
 * screen; it lives in the app's private files and never leaves the device.
 *
 * Created once per install and reused, so a helper started after a reboot still
 * accepts the app that started it.
 */
class DisplayToken(context: Context) {
    private val file = File(context.filesDir, "display-token")

    val value: String = readOrCreate()

    private fun readOrCreate(): String {
        val saved = runCatching { file.readText().trim() }.getOrNull()
        if (saved != null && saved.length >= MIN_LENGTH) {
            return saved
        }
        val fresh = UUID.randomUUID().toString().replace("-", "") +
            UUID.randomUUID().toString().replace("-", "")
        runCatching {
            file.writeText(fresh)
            // Owner-only: the app is the only client the helper cares about.
            file.setReadable(false, false)
            file.setReadable(true, true)
            file.setWritable(false, false)
            file.setWritable(true, true)
        }
        return fresh
    }

    private companion object {
        const val MIN_LENGTH = 32
    }
}
