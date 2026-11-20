package com.hopa.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.hopa.feature.runs.RunsScreen
import com.hopa.ui.theme.HopaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The audit trail with nothing in it, which is where every install starts. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class RunsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun empty() {
        compose.setContent {
            HopaTheme {
                RunsScreen(
                    records = emptyList(),
                    onBack = {},
                    onVerify = { true },
                    onClear = {},
                )
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/runs-empty.png")
    }
}
