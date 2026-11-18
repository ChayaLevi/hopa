package com.hopa.app.feature.execution

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.provider.Settings
import com.hopa.app.MainActivity
import com.hopa.app.di.AppContainer
import com.hopa.capabilities.accessibility.HopaAccessibilityService
import com.hopa.capabilities.accessibility.ScreenTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Pairs this phone with its own adbd by reading Android's pairing dialog.
 *
 * The pairing port stops listening as soon as the dialog loses the focus, so a
 * code typed in HOPA can never reach it. This opens the dialog, reads the code
 * and the port with accessibility, and pairs from the background while the
 * dialog is still in front.
 */
class WirelessSetup(
    private val container: AppContainer,
) {
    private val verbose =
        (container.context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    suspend fun run(): String = withContext(Dispatchers.Main) { pair() }

    /**
     * The person opens the pairing dialog; HOPA only waits for it, reads the
     * code and pairs. Used when the settings list cannot be driven reliably.
     */
    suspend fun waitForDialog(): String = withContext(Dispatchers.Main) {
        val message = waitAndPair()
        returnToApp()
        message
    }

    private suspend fun waitAndPair(): String {
        val service = HopaAccessibilityService.instance
            ?: return "Turn on screen access first: HOPA reads the code from Android's dialog."
        val screen = ScreenTree(service)
        val pairing = awaitPairing(screen, WAIT_FOR_DIALOG_MS)
            ?: return "No pairing dialog appeared. Open Wireless debugging, then Pair device with pairing code."
        return container.pairWireless(pairing.first, pairing.second)
    }

    /** The screen work is on the main thread; pairing itself switches to IO. */
    private suspend fun pair(): String {
        val message = attempt()
        returnToApp()
        return message
    }

    private suspend fun attempt(): String {
        val service = HopaAccessibilityService.instance
            ?: return "Turn on screen access first: HOPA reads the code from Android's dialog."
        val screen = ScreenTree(service)

        openDeveloperOptions()
        // The pairing row sits below the fold, so wait for the page itself and
        // then scroll; waiting for the row first would give up too early.
        if (!awaitText(screen, "Developer options", OPEN_WAIT_MS)) {
            return "Android's Developer options did not open."
        }
        if (verbose) android.util.Log.d("HopaSetup", "page=${screen.texts().take(4)}")
        if (!clickRow(screen, "Wireless debugging")) {
            return "Could not open Wireless debugging."
        }
        if (!awaitText(screen, "Pair device with pairing code", OPEN_WAIT_MS)) {
            return "The Wireless debugging page did not show its pairing row."
        }
        if (!clickRow(screen, "Pair device with pairing code")) {
            return "Could not press \"Pair device with pairing code\"."
        }
        val pairing = awaitPairing(screen)
            ?: return "Android's pairing dialog did not appear; is Wireless debugging on?"
        return container.pairWireless(pairing.first, pairing.second)
    }

    private fun openDeveloperOptions() {
        // The action alone can resume an existing Settings task where another
        // page is on top (About phone); the explicit activity always lands on
        // the list. The action stays as the fallback for other builds.
        val explicit = Intent()
            .setComponent(DEVELOPER_OPTIONS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val opened = runCatching { container.context.startActivity(explicit) }.isSuccess
        if (!opened) {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { container.context.startActivity(intent) }
        }
    }

    private suspend fun awaitText(screen: ScreenTree, part: String, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (screen.hasText(part)) {
                return true
            }
            delay(POLL_MS)
        }
        return false
    }

    /**
     * Find a row and press it, scrolling one page at a time. Search and press
     * happen in the same step: a row found during a fling can be gone before a
     * separate click runs.
     */
    private suspend fun clickRow(screen: ScreenTree, part: String): Boolean {
        repeat(SCROLLS) { attempt ->
            if (screen.clickText(part)) {
                if (verbose) android.util.Log.d("HopaSetup", "pressed \"$part\" on attempt $attempt")
                delay(SETTLE_MS)
                return true
            }
            if (verbose) android.util.Log.d("HopaSetup", "not pressable yet: \"$part\" (attempt $attempt)")
            screen.scrollForward()
            delay(SETTLE_MS)
            if (verbose) android.util.Log.d("HopaSetup", "after scroll: ${screen.texts().take(3)}")
        }
        return false
    }

    private suspend fun awaitPairing(
        screen: ScreenTree,
        timeoutMs: Long = PAIRING_WAIT_MS,
    ): Pair<String, String>? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val texts = screen.texts()
            val endpoint = texts.firstOrNull { endpointPattern.matches(it) }
            val code = texts.firstOrNull { codePattern.matches(it) }
            if (endpoint != null && code != null) {
                return endpoint to code
            }
            delay(POLL_MS)
        }
        return null
    }

    private fun returnToApp() {
        val intent = Intent(container.context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        runCatching { container.context.startActivity(intent) }
    }

    private companion object {
        const val POLL_MS = 250L
        const val SETTLE_MS = 600L
        const val OPEN_WAIT_MS = 10_000L
        const val PAIRING_WAIT_MS = 20_000L
        const val WAIT_FOR_DIALOG_MS = 120_000L
        const val SCROLLS = 18

        val DEVELOPER_OPTIONS = android.content.ComponentName(
            "com.android.settings",
            "com.android.settings.Settings\$DevelopmentSettingsActivity",
        )
        val endpointPattern = Regex("""\d{1,3}(?:\.\d{1,3}){3}:\d{2,5}""")
        val codePattern = Regex("""\d{6}""")
    }
}
