package com.hopa.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.hopa.feature.history.ActivityScreen
import com.hopa.feature.history.ActivityUiState
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
 * The same screens on a small phone.
 *
 * A 360dp width is where long labels wrap and rows start to overflow, which is
 * exactly what a JVM render can catch without a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class CompactScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun settings() {
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
        compose.onRoot().captureRoboImage("src/test/screenshots/compact-settings.png")
    }

    @Test
    fun activity() {
        compose.setContent {
            HopaTheme {
                ActivityScreen(
                    state = ActivityUiState(
                        weekActions = 12,
                        weekDone = 10,
                        weekApps = 3,
                        lastGoal = "Order the usual coffee",
                        lastSteps = listOf("Open the cafe app", "Pick up the usual", "Pay"),
                        ready = true,
                    ),
                    activeTab = "activity",
                    onSelectTab = {},
                    onOpenRuns = {},
                    onOpenUsage = {},
                    onOpenHistory = {},
                    onOpenRecipes = {},
                    onShare = {},
                    onSave = {},
                    onUnavailable = {},
                )
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/compact-activity.png")
    }
}
