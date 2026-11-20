package com.hopa.feature.memory

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun MemoryRoute(
    onBack: () -> Unit,
) {
    val viewModel: MemoryViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    MemoryScreen(
        state = state,
        onBack = onBack,
        onAddFact = viewModel::addFact,
        onDeleteFact = viewModel::deleteFact,
        onClearFacts = viewModel::clearFacts,
        onExport = {
            val text = buildString {
                appendLine("HOPA memory")
                state.facts.forEach { appendLine("- $it") }
            }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(send, "Export memory"))
        },
    )
}
