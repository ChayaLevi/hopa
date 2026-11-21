package com.hopa.decision.classifier

import com.hopa.contracts.ports.InstalledApp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class IntentClassifierTest {
    private val apps = listOf(
        InstalledApp("com.android.settings", "Settings"),
        InstalledApp("com.android.settings", "Настройки"),
        InstalledApp("com.google.android.youtube", "YouTube"),
        InstalledApp("com.example.bank", "Bank"),
    )
    private val classifier = IntentClassifier()

    @Test
    fun `launch commands in both languages`() {
        val launchers = listOf(
            "открой настройки",
            "Открой Настройки!",
            "открыть настройки",
            "запусти настройки",
            "пожалуйста, открой настройки",
            "open settings",
            "launch settings",
            "start settings",
            "can you open settings",
            "открой приложение ютуб".replace("ютуб", "settings"),
        )
        for (text in launchers) {
            val match = classifier.classify(text, apps)
            assertNotNull(match, text)
            assertEquals("ui.launch", match!!.intent.action.kind, text)
            assertEquals("com.android.settings", match.intent.action.app, text)
        }
    }

    @Test
    fun `navigation and scroll commands`() {
        val cases = mapOf(
            "назад" to "ui.back",
            "go back" to "ui.back",
            "домой" to "ui.home",
            "go home" to "ui.home",
            "прокрути вниз" to "ui.scroll",
            "scroll down" to "ui.scroll",
            "пролистай вверх" to "ui.scroll",
            "scroll up" to "ui.scroll",
            "подожди" to "ui.wait",
            "wait a moment" to "ui.wait",
        )
        for ((text, kind) in cases) {
            val match = classifier.classify(text, apps)
            assertNotNull(match, text)
            assertEquals(kind, match!!.intent.action.kind, text)
        }
        assertEquals("down", classifier.classify("scroll down", apps)!!.intent.action.direction)
        assertEquals("up", classifier.classify("scroll up", apps)!!.intent.action.direction)
    }

    @Test
    fun `the label is used for the answer`() {
        assertEquals("Opened Настройки.", classifier.classify("открой настройки", apps)!!.intent.answer)
    }

    @Test
    fun `a package name is accepted`() {
        val match = classifier.classify("open com.android.settings", apps)
        assertNotNull(match)
        assertEquals("com.android.settings", match!!.intent.action.app)
    }

    @Test
    fun `an unknown app falls back to the model`() {
        assertNull(classifier.classify("открой дверь", apps))
        assertNull(classifier.classify("open the pod bay doors", apps))
        assertNull(classifier.classify("открой настройки вайфай", apps))
        assertNull(classifier.classify("открой", apps))
        assertNull(classifier.classify("open", apps))
    }

    @Test
    fun `a goal that is not a command falls back to the model`() {
        assertNull(classifier.classify("купи хлеб", apps))
        assertNull(classifier.classify("what is on the screen", apps))
        assertNull(classifier.classify("", apps))
    }

    @Test
    fun `an ambiguous app name falls back to the model`() {
        val twoBanks = listOf(
            InstalledApp("com.a.bank", "Bank A"),
            InstalledApp("com.b.bank", "Bank B"),
        )
        assertNull(classifier.classify("открой bank", twoBanks))
    }
}
