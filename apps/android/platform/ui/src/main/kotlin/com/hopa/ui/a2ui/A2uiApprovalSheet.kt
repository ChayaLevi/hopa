package com.hopa.ui.a2ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.contracts.a2ui.A2uiAction
import com.hopa.contracts.a2ui.A2uiComponent
import com.hopa.contracts.a2ui.A2uiSurface
import com.hopa.ui.theme.HopaColor
import java.time.Instant

/**
 * An approval surface drawn as HOPA's approval dialog. The caller has already
 * checked it with `ApprovalSurfaces.problem`. Dismissing is a tap on deny; a
 * payment's allow stays disabled until its notice is acknowledged. A surface
 * that does not have the approval shape is answered with deny, never drawn.
 */
@Composable
fun A2uiApprovalSheet(surface: A2uiSurface, onAction: (A2uiAction) -> Unit) {
    val layout = remember(surface) { ApprovalLayout.of(surface) }
    if (layout == null) {
        LaunchedEffect(surface.id) { denyTap(surface)?.let(onAction) }
        return
    }
    val payment = layout.notice != null
    var acknowledged by remember(surface.id) { mutableStateOf(!payment) }
    val scope = A2uiScope(surface, acknowledged, { acknowledged = it }, onAction)
    val deny = { scope.tap(layout.deny) }
    AlertDialog(
        onDismissRequest = deny,
        title = {
            Column {
                if (payment) {
                    Surface(
                        color = HopaColor.Danger.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            "PAYMENT",
                            color = HopaColor.Danger,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                Text(
                    surface.text(layout.title.text),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                layout.body.forEach { component ->
                    if (component is A2uiComponent.PaymentNotice) {
                        PaymentNoticeLines(surface, component)
                    } else {
                        A2uiComponentView(scope, component)
                    }
                }
                // The acknowledgement closes the body, right above the buttons it unlocks.
                layout.notice?.let { notice ->
                    AcknowledgeRow(surface.text(notice.acknowledge), acknowledged) { acknowledged = it }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { scope.tap(layout.approve) }, enabled = acknowledged) {
                Text(
                    label(surface, layout.approve),
                    color = when {
                        !acknowledged -> HopaColor.TextHint
                        payment -> HopaColor.Danger
                        else -> HopaColor.Primary
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = deny) {
                Text(label(surface, layout.deny), color = HopaColor.TextSecondary)
            }
        },
    )
}

private fun label(surface: A2uiSurface, button: A2uiComponent.Button): String =
    (surface.component(button.child) as? A2uiComponent.Text)?.let { surface.text(it.text) }.orEmpty()

/** Any deny button's tap, so a malformed surface still ends in a refusal the caller understands. */
private fun denyTap(surface: A2uiSurface): A2uiAction? = surface.components.values
    .filterIsInstance<A2uiComponent.Button>()
    .firstOrNull { it.event.name == com.hopa.contracts.a2ui.ApprovalSurfaces.DENY }
    ?.let { surface.tap(it.id, Instant.now()) }
