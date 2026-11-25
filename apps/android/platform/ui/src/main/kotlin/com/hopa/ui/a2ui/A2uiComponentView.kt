package com.hopa.ui.a2ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.contracts.a2ui.A2uiAction
import com.hopa.contracts.a2ui.A2uiComponent
import com.hopa.contracts.a2ui.A2uiSurface
import com.hopa.contracts.a2ui.ApprovalSurfaces
import com.hopa.ui.theme.HopaColor
import java.time.Instant

@Composable
fun A2uiComponentView(scope: A2uiScope, component: A2uiComponent) {
    val surface = scope.surface
    when (component) {
        is A2uiComponent.Text -> TextLine(surface.text(component.text), caption = component.variant == "caption")
        is A2uiComponent.Column -> Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = when (component.align) {
                "center" -> Alignment.CenterHorizontally
                "end" -> Alignment.End
                else -> Alignment.Start
            },
        ) { surface.children(component).forEach { A2uiComponentView(scope, it) } }
        is A2uiComponent.Row -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = when (component.justify) {
                "center" -> Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                "end" -> Arrangement.spacedBy(8.dp, Alignment.End)
                "spaceBetween" -> Arrangement.SpaceBetween
                else -> Arrangement.spacedBy(8.dp)
            },
            verticalAlignment = Alignment.CenterVertically,
        ) { surface.children(component).forEach { A2uiComponentView(scope, it) } }
        is A2uiComponent.Card -> Card {
            Column(Modifier.padding(16.dp)) {
                surface.children(component).forEach { A2uiComponentView(scope, it) }
            }
        }
        is A2uiComponent.Button -> TextButton(onClick = { scope.tap(component) }, enabled = scope.enabled(component)) {
            surface.children(component).forEach { A2uiComponentView(scope, it) }
        }
        is A2uiComponent.Divider ->
            if (component.axis == "vertical") VerticalDivider() else HorizontalDivider()
        is A2uiComponent.Icon -> Icon(icon(component.name), contentDescription = null)
        is A2uiComponent.PaymentNotice -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PaymentNoticeLines(surface, component)
            AcknowledgeRow(surface.text(component.acknowledge), scope.acknowledged, scope.onAcknowledge)
        }
    }
}

/** A Text component: body is 14 sp in the surrounding colour, caption is 11 sp hint. */
@Composable
private fun TextLine(text: String, caption: Boolean) {
    if (caption) {
        Text(text, fontSize = 11.sp, color = HopaColor.TextHint)
    } else {
        Text(text, fontSize = 14.sp)
    }
}


private fun icon(name: String): ImageVector = when (name) {
    "check" -> Icons.Filled.Check
    "close" -> Icons.Filled.Close
    "lock" -> Icons.Filled.Lock
    "send" -> Icons.Filled.Send
    "payment", "shoppingCart" -> Icons.Filled.ShoppingCart
    "warning", "error" -> Icons.Filled.Warning
    else -> Icons.Filled.Info
}
