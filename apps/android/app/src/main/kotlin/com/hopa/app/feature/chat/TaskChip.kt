package com.hopa.app.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.app.runtime.TaskItem
import com.hopa.ui.theme.HopaColor

/** One background task: its goal, a state light, and a way in. */
@Composable
fun TaskChip(
    task: TaskItem,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dot = when (task.state) {
        "running" -> HopaColor.Primary
        "done" -> HopaColor.Success
        else -> HopaColor.Danger
    }
    Row(
        modifier = Modifier
            .background(HopaColor.SurfaceAlt, RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .background(dot, androidx.compose.foundation.shape.CircleShape)
                .padding(3.dp),
        )
        Text(
            task.goal,
            fontSize = 12.sp,
            color = HopaColor.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 2.dp),
        )
        if (task.state != "running") {
            Text(
                "\u00d7",
                fontSize = 14.sp,
                color = HopaColor.TextHint,
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 6.dp),
            )
        }
    }
}
