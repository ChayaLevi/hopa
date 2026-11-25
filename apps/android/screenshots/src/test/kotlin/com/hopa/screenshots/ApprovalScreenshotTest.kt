package com.hopa.screenshots

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.hopa.contracts.approval.ApprovalRequest
import com.hopa.contracts.wire.Action
import com.hopa.ui.component.ApprovalSheet
import com.hopa.ui.theme.HopaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The approval sheet, pinned before and after it became an A2UI surface
 * (ADR-0031 phase 2): the pictures must not move, and neither may the rules -
 * deny on dismiss, and no allow on a payment until it is acknowledged.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ApprovalScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val plain = ApprovalRequest(
        title = "Open Settings?",
        body = "HOPA wants to open Wi-Fi settings on your phone.",
        amount = "",
        merchant = "",
        consequence = "",
        isPayment = false,
        action = Action(kind = "launch"),
    )
    private val payment = ApprovalRequest(
        title = "Confirm payment",
        body = "Money may leave your account.",
        amount = "12.40",
        merchant = "Example Shop",
        consequence = "HOPA cannot undo a payment.",
        isPayment = true,
        action = Action(kind = "tap"),
    )
    private val withAmount = plain.copy(
        title = "Send the transfer note?",
        body = "HOPA will type the note in your bank app.",
        amount = "5.00",
        merchant = "City Bank",
        action = Action(kind = "type"),
    )
    private val answers = mutableListOf<String>()

    private fun show(request: ApprovalRequest) {
        compose.setContent {
            HopaTheme {
                ApprovalSheet(
                    request = request,
                    onDeny = { answers += "deny" },
                    onAllow = { answers += "allow" },
                )
            }
        }
    }

    @Test
    fun plain() {
        show(plain)
        compose.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/approval-plain.png")
        compose.onNodeWithText("Allow").assertIsEnabled().performClick()
        assertEquals(listOf("allow"), answers)
    }

    @Test
    fun plainWithAmount() {
        show(withAmount)
        compose.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/approval-amount.png")
        compose.onNodeWithText("Deny").performClick()
        assertEquals(listOf("deny"), answers)
    }

    @Test
    fun payment() {
        show(payment)
        compose.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/approval-payment.png")
        compose.onNodeWithText("Allow").assertIsNotEnabled()
    }

    @Test
    fun paymentAcknowledged() {
        show(payment)
        compose.onNodeWithText("I understand money may leave my account.").performClick()
        compose.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/approval-payment-acknowledged.png")
        compose.onNodeWithText("Allow").assertIsEnabled().performClick()
        assertEquals(listOf("allow"), answers)
    }
}
