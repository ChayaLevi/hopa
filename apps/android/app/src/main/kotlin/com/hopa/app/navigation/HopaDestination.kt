package com.hopa.app.navigation

object HopaDestination {
    const val CHAT = "chat"
    const val SETTINGS = "settings"
    const val PROFILES = "profiles"
    const val RUNS = "runs"
    const val USAGE = "usage"
    const val MEMORY = "memory"
    const val SKILLS = "skills"
    const val RUNNING_APPS = "runningapps"
    const val ACT_MODE = "actmode"
    const val RUN_SETUP = "runsetup"
    const val RUN_PREFS = "runprefs"
    const val COMPUTER = "computer"
    const val BROWSER = "browser"
    const val SELF_CHECK = "selfcheck"
    const val ABOUT = "about"
    const val CAPABILITIES = "capabilities"
    const val ACCESSIBILITY = "accessibility"
    const val LAUNCHPAD = "launchpad"

    /** The catalog, opened on Apps or Sites. */
    fun launchpad(section: String): String = "$LAUNCHPAD?section=$section"
    const val APP_CARD = "appcard"
}
