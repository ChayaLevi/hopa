package com.hopa.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.hopa.feature.history.ActivityScreen
import com.hopa.feature.history.ActivityUiState
import com.hopa.ui.theme.HopaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The what-happened page, with and without a week behind it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ActivityScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun empty() {
        compose.setContent {
            HopaTheme {
                ActivityScreen(
                    state = ActivityUiState(),
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
        compose.onRoot().captureRoboImage("src/test/screenshots/activity-empty.png")
    }

    @Test
    fun week() {
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
        compose.onRoot().captureRoboImage("src/test/screenshots/activity-week.png")
    }
}
