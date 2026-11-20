package com.hopa.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.hopa.feature.settings.SettingsScreen
import com.hopa.feature.settings.SettingsUiState
import com.hopa.ui.theme.HopaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The settings hub as a person first sees it and after a key is stored.
 *
 * The images are the review surface: a diff means the screen changed, on
 * purpose or by accident.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun hub() {
        compose.setContent {
            HopaTheme {
                SettingsScreen(
                    state = SettingsUiState(),
                    onBack = {},
                    onOpenPage = {},
                    onTone = {},
                    onChattiness = {},
                    onMascot = {},
                    onToggleAlerts = {},
                    onOpenProfiles = {},
                    onOpenSelfCheck = {},
                    onOpenAbout = {},
                )
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/settings-hub.png")
    }

    @Test
    fun configured() {
        compose.setContent {
            HopaTheme {
                SettingsScreen(
                    state = SettingsUiState(keySaved = true, phoneAccess = true),
                    onBack = {},
                    onOpenPage = {},
                    onTone = {},
                    onChattiness = {},
                    onMascot = {},
                    onToggleAlerts = {},
                    onOpenProfiles = {},
                    onOpenSelfCheck = {},
                    onOpenAbout = {},
                )
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/settings-configured.png")
    }
}
