package com.hopa.feature.conversations

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.hopa.ui.theme.HopaColor
import com.hopa.contracts.chat.Conversation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsSheet(
    currentId: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onNewChat: () -> Unit,
) {
    val viewModel: ConversationsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var searchOpen by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Conversation?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        viewModel.onQuery("")
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearMessage()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HopaColor.Surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.92f)
                .padding(bottom = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Chats",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = HopaColor.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { viewModel.sync() },
                ) {
                    if (state.syncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = HopaColor.Primary,
                        )
                    } else {
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = "Sync chats",
                            tint = HopaColor.TextSecondary,
                        )
                    }
                }
                IconButton(
                    onClick = {
                        searchOpen = !searchOpen
                        if (!searchOpen) {
                            viewModel.onQuery("")
                        }
                    },
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search chats",
                        tint = if (searchOpen) HopaColor.Primary else HopaColor.TextSecondary,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close history",
                        tint = HopaColor.TextSecondary,
                    )
                }
            }

            if (searchOpen) {
                BasicTextField(
                    value = state.query,
                    onValueChange = viewModel::onQuery,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 13.sp, color = HopaColor.TextPrimary),
                    cursorBrush = SolidColor(HopaColor.Primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .padding(horizontal = 16.dp)
                        .background(HopaColor.SurfaceMuted, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp),
                    decorationBox = { inner ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (state.query.isEmpty()) {
                                Text(
                                    "Search chats",
                                    fontSize = 13.sp,
                                    color = HopaColor.TextHint,
                                )
                            }
                            inner()
                        }
                    },
                )
                Box(modifier = Modifier.height(6.dp))
            }

            NewChatRow {
                viewModel.newChat { onNewChat() }
            }

            val real = state.conversations.filter { it.messages.isNotEmpty() }
            val searching = state.query.isNotBlank()
            val current = if (searching) null else real.firstOrNull { it.id == currentId }
            val others = real.filterNot { it.id == currentId || (current != null && it.id == current.id) }
            val visible = if (searching) real else others
            val sections = conversationSections(visible)

            if (current != null) {
                SectionLabel("Current")
                ChatRow(
                    conversation = current,
                    current = true,
                    onClick = {
                        onSelect(current.id)
                    },
                    onDelete = { deleteTarget = current },
                )
            }

            if (sections.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    sections.forEach { section ->
                        item(key = "header-${section.title}") {
                            SectionLabel(section.title)
                        }
                        items(section.items, key = Conversation::id) { conversation ->
                            SwipeRow(
                                conversation = conversation,
                                current = false,
                                onClick = { onSelect(conversation.id) },
                                onDelete = { deleteTarget = conversation },
                            )
                        }
                    }
                }
            } else if (current == null) {
                Text(
                    if (searching) "No chats match your search." else "No chats yet.",
                    fontSize = 12.sp,
                    color = HopaColor.TextHint,
                    modifier = Modifier.padding(start = 12.dp, top = 12.dp),
                )
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete this chat?") },
            text = { Text(target.title.ifBlank { "Untitled chat" }) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(target.id)
                        deleteTarget = null
                    },
                ) { Text("Delete", color = HopaColor.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        label.uppercase(),
        fontSize = 10.sp,
        color = HopaColor.TextHint,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun NewChatRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .padding(horizontal = 10.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(HopaColor.PrimarySoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", fontSize = 18.sp, color = HopaColor.Primary)
        }
        Text(
            "New chat",
            fontSize = 14.sp,
            color = HopaColor.Primary,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRow(
    conversation: Conversation,
    current: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismiss = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                false
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismiss,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 8.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 76.dp, height = 44.dp)
                        .background(HopaColor.Danger, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = HopaColor.OnPrimary,
                    )
                }
            }
        },
    ) {
        ChatRow(conversation, current, onClick, onDelete)
    }
}

@Composable
private fun ChatRow(
    conversation: Conversation,
    current: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(
                if (current) HopaColor.PrimarySoft else HopaColor.Surface,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            conversation.title.ifBlank { "Untitled chat" },
            fontSize = 14.sp,
            color = HopaColor.TextPrimary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (current) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Current chat",
                tint = HopaColor.Primary,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}
