package com.hopa.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.hopa.feature.settings.DecisionLayerInfo
import com.hopa.feature.settings.DecisionsScreen
import com.hopa.feature.settings.SettingsUiState
import com.hopa.stores.AppSettings
import com.hopa.ui.theme.HopaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Decisions page with one default and one shadow layer. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class DecisionsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun automatic() {
        val state = SettingsUiState(
            settings = AppSettings(decisionMode = "auto", shadowDecisions = true),
            decisionLayers = listOf(
                DecisionLayerInfo(
                    id = "rules",
                    title = "Rules",
                    detail = "1.0.0 · micro · up to dangerous actions",
                    summary = "Heuristics for known commands; acts on reads, asks for changes, " +
                        "payments and deletes.",
                    howTo = "It decides the steps it is allowed to; the other layers keep voting.",
                ),
                DecisionLayerInfo(
                    id = "laix",
                    title = "LAIX",
                    detail = "0.18.0 · micro · up to dangerous actions",
                    summary = "Live decision policy: wait, observe, ask, act, verify or escalate " +
                        "with a reason and a score.",
                    howTo = "It votes on every step and is recorded, but it does not decide yet.",
                ),
            ),
            decisionActive = mapOf(
                "read" to "rules",
                "change" to "model",
                "dangerous" to "model",
            ),
        )
        compose.setContent {
            HopaTheme {
                DecisionsScreen(
                    state = state,
                    onBack = {},
                    onMode = {},
                    onShadow = {},
                    onInstantCommands = {},
                )
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/decisions-automatic.png")
    }
}
