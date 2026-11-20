package com.hopa.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.hopa.data.database.entity.RunHistoryEntity
import kotlinx.coroutines.launch

/** Finished turns, with the surface filter and one clear action. */
@Composable
fun HistoryRoute(
    container: HistoryDependencies,
    onBack: () -> Unit,
) {
    var rows by remember { mutableStateOf(emptyList<RunHistoryEntity>()) }
    var filter by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(filter) {
        rows = container.runHistory.latest(filter.ifBlank { null })
    }

    HistoryScreen(
        rows = rows,
        selected = filter,
        onFilter = { filter = it },
        onBack = onBack,
        onClear = {
            scope.launch {
                container.runHistory.clear()
                rows = emptyList()
            }
        },
    )
}
