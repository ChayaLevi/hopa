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

/**
 * Draws a validated [A2uiSurface] with HOPA's own Compose components
 * (ADR-0027). It only ever receives a surface that `A2uiSession` accepted,
 * so every child exists and every binding is a string. Text is plain text,
 * never Markdown. A tap becomes an A2UI `action` through [A2uiSurface.tap].
 *
 * The approval sheet and the chat's choice chips lay out their own surfaces
 * ([A2uiApprovalSheet], [ChoiceChips]); this view is the general case.
 */
@Composable
fun A2uiSurfaceView(
    surface: A2uiSurface,
    onAction: (A2uiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var acknowledged by remember(surface.id) { mutableStateOf(false) }
    val scope = A2uiScope(
        surface = surface,
        acknowledged = acknowledged,
        onAcknowledge = { acknowledged = it },
        onAction = onAction,
    )
    Column(modifier) { A2uiComponentView(scope, surface.root) }
}
