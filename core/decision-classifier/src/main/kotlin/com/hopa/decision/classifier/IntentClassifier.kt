package com.hopa.decision.classifier

import com.hopa.contracts.ports.InstalledApp
import com.hopa.contracts.wire.Action

/**
 * A closed set of commands that need no model.
 *
 * The classifier is deliberately small and deterministic: whole-phrase matches
 * and app labels, with a confidence the caller compares against its threshold.
 * Anything it does not recognize returns null, which means "ask the model", not
 * "guess". It never classifies a tap, a payment or anything that changes a
 * setting: those need the screen the model reads.
 */
class IntentClassifier(
    private val threshold: Double = 0.9,
) {
    fun classify(text: String, installedApps: List<InstalledApp>): IntentMatch? {
        val phrase = CommandText.normalize(text)
        if (phrase.isEmpty()) {
            return null
        }
        launch(phrase, installedApps)?.let { return it }
        launchByPackage(text, installedApps)?.let { return it }
        back(phrase)?.let { return it }
        home(phrase)?.let { return it }
        scroll(phrase)?.let { return it }
        wait(phrase)?.let { return it }
        return null
    }

    private fun launch(phrase: String, apps: List<InstalledApp>): IntentMatch? {
        val match = LAUNCH.matchEntire(phrase) ?: return null
        val requested = match.groupValues[1].trim()
        if (requested.isEmpty()) {
            return null
        }
        val target = requested.removePrefix("приложение ").removePrefix("app ").trim()
        val (app, confidence) = resolveApp(target, apps) ?: return null
        if (confidence < threshold) {
            return null
        }
        return launchMatch(app, confidence)
    }

    /**
     * A package name typed in full. Normalization turns dots into spaces, so
     * this reads the phrase before that step; it accepts only an exact package,
     * never a guess.
     */
    private fun launchByPackage(text: String, apps: List<InstalledApp>): IntentMatch? {
        val cleaned = text.lowercase().trim().trimEnd('.', '!', '?', ',')
        val match = LAUNCH.matchEntire(cleaned) ?: return null
        val requested = match.groupValues[1].trim()
        if (!requested.contains('.')) {
            return null
        }
        val app = apps.firstOrNull { it.packageName.equals(requested, ignoreCase = true) } ?: return null
        return launchMatch(app, 0.96)
    }

    private fun launchMatch(app: InstalledApp, confidence: Double): IntentMatch = IntentMatch(
        intent = Intent(
            id = "launch.${app.packageName}",
            action = Action(kind = "ui.launch", app = app.packageName),
            answer = "Opened ${app.label}.",
        ),
        confidence = confidence,
    )

    /**
     * The best installed app for a spoken name: the exact label, the exact
     * package, a package suffix, then a label the name starts. A name that fits
     * several apps equally is ambiguous and returns null, so the model decides.
     */
    private fun resolveApp(target: String, apps: List<InstalledApp>): Pair<InstalledApp, Double>? {
        val wanted = CommandText.normalize(target)
        if (wanted.isEmpty()) {
            return null
        }
        val labelled = apps.map { it to CommandText.normalize(it.label) }
        labelled.firstOrNull { (_, label) -> label == wanted }?.let { return it.first to 0.97 }
        apps.firstOrNull { it.packageName.equals(wanted, ignoreCase = true) }
            ?.let { return it to 0.96 }
        val bySuffix = apps
            .filter { it.packageName.endsWith(".$wanted", ignoreCase = true) }
            .distinctBy { it.packageName }
        if (bySuffix.size == 1) {
            return bySuffix.single() to 0.93
        }
        val prefixed = labelled.filter { (_, label) -> wanted.length >= 4 && label.startsWith(wanted) }
        if (prefixed.size == 1) {
            return prefixed.single().first to 0.9
        }
        return null
    }

    private fun back(phrase: String): IntentMatch? {
        if (phrase !in BACK) {
            return null
        }
        return IntentMatch(
            intent = Intent("back", Action(kind = "ui.back"), "Went back."),
            confidence = 0.97,
        )
    }

    private fun home(phrase: String): IntentMatch? {
        if (phrase !in HOME) {
            return null
        }
        return IntentMatch(
            intent = Intent("home", Action(kind = "ui.home"), "Went home."),
            confidence = 0.97,
        )
    }

    private fun scroll(phrase: String): IntentMatch? {
        val direction = when (phrase) {
            in SCROLL_DOWN -> "down"
            in SCROLL_UP -> "up"
            else -> return null
        }
        return IntentMatch(
            intent = Intent(
                "scroll.$direction",
                Action(kind = "ui.scroll", direction = direction),
                "Scrolled $direction.",
            ),
            confidence = 0.95,
        )
    }

    private fun wait(phrase: String): IntentMatch? {
        if (phrase !in WAIT) {
            return null
        }
        return IntentMatch(
            intent = Intent("wait", Action(kind = "ui.wait", timeoutMs = 1_000), "Waited a moment."),
            confidence = 0.95,
        )
    }

    private companion object {
        val LAUNCH = Regex("^(?:открой|открыть|запусти|запустить|open|launch|start)\\s+(.+)$")
        val BACK = setOf("назад", "back", "go back", "вернись назад")
        val HOME = setOf("домой", "на главный экран", "home", "go home", "home screen")
        val SCROLL_DOWN = setOf("прокрути вниз", "пролистай вниз", "листай вниз", "scroll down")
        val SCROLL_UP = setOf("прокрути вверх", "пролистай вверх", "листай вверх", "scroll up")
        val WAIT = setOf("подожди", "wait", "wait a moment", "wait a second", "подожди немного")
    }
}
