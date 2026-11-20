package com.hopa.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.hopa.feature.settings.LocalModelSection
import com.hopa.feature.settings.LocalModelUiState
import com.hopa.inference.local.InstalledModel
import com.hopa.inference.local.LocalModelCatalogue
import com.hopa.ui.theme.HopaColor
import com.hopa.ui.theme.HopaTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The on-device section: offered, and with one model installed. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class LocalModelSectionTest {
    @get:Rule
    val compose = createComposeRule()

    private val recommended = LocalModelCatalogue.models.first { it.id == "qwen3-1.7b" }

    @Test
    fun offered() {
        compose.setContent {
            HopaTheme {
                Column(
                    modifier = Modifier
                        .background(HopaColor.Bg)
                        .padding(16.dp),
                ) {
                    LocalModelSection(
                        state = LocalModelUiState(recommended = recommended, ramGb = 6),
                        onImport = {},
                        onDelete = {},
                        onDeleteAll = {},
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/local-model-offered.png")
    }

    @Test
    fun installed() {
        compose.setContent {
            HopaTheme {
                Column(
                    modifier = Modifier
                        .background(HopaColor.Bg)
                        .padding(16.dp),
                ) {
                    LocalModelSection(
                        state = LocalModelUiState(
                            recommended = recommended,
                            installed = listOf(
                                InstalledModel(
                                    id = "qwen3-1.7b",
                                    file = File("qwen3-1.7b-1234.gguf"),
                                    bytes = 1_100_000_000L,
                                ),
                            ),
                            installedSpecs = mapOf("qwen3-1.7b" to recommended),
                            totalBytes = 1_100_000_000L,
                            ramGb = 6,
                        ),
                        onImport = {},
                        onDelete = {},
                        onDeleteAll = {},
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/local-model-installed.png")
    }
}
