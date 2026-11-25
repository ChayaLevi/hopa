package com.hopa.screenshots

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.hopa.contracts.a2ui.A2uiAction
import com.hopa.contracts.a2ui.A2uiCodec
import com.hopa.contracts.a2ui.A2uiOutcome
import com.hopa.contracts.a2ui.A2uiSession
import com.hopa.contracts.a2ui.ChoiceSurfaces
import com.hopa.contracts.approval.ApprovalRequest
import com.hopa.contracts.wire.Action
import com.hopa.ui.a2ui.A2uiSurfaceView
import com.hopa.ui.component.ApprovalSheet
import com.hopa.ui.theme.HopaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The general A2UI view (ADR-0031), and the sheet failing closed. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class A2uiSurfaceScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun choiceCard() {
        val options = listOf("Personal", "Work", "Family")
        val message = ChoiceSurfaces.build("Which account?", options, "ask_turn_7_1").single()
        val surface = (A2uiSession().apply(A2uiCodec.encode(message)) as A2uiOutcome.Ready).surface
        val taps = mutableListOf<A2uiAction>()
        compose.setContent {
            HopaTheme { A2uiSurfaceView(surface, onAction = { taps += it }, modifier = Modifier.padding(16.dp)) }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/a2ui-choice-card.png")
        compose.onNodeWithText("Family").performClick()
        assertEquals("Family", ChoiceSurfaces.chosen(taps.single(), surface.id, options))
    }

    @Test
    fun aRequestThatCannotBecomeACardIsDenied() {
        val answers = mutableListOf<String>()
        val request = ApprovalRequest(
            title = "Too big to carry",
            body = "x".repeat(40_000),
            amount = "",
            merchant = "",
            consequence = "",
            isPayment = false,
            action = Action(kind = "tap"),
        )
        compose.setContent {
            HopaTheme { ApprovalSheet(request, onDeny = { answers += "deny" }, onAllow = { answers += "allow" }) }
        }
        compose.waitForIdle()
        assertEquals(listOf("deny"), answers)
    }
}
