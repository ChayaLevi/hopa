package com.hopa.feature.history

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext

@Composable
fun ActivityRoute(
    container: ActivityDependencies,
    activeTab: String,
    onSelectTab: (String) -> Unit,
    onOpenRuns: () -> Unit,
    onOpenUsage: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenRecipes: () -> Unit,
) {
    val viewModel: ActivityViewModel = viewModel(factory = ActivityViewModelFactory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val saveWeek = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(viewModel.shareText().toByteArray())
            }
            Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    ActivityScreen(
        state = state,
        activeTab = activeTab,
        onSelectTab = onSelectTab,
        onOpenRuns = onOpenRuns,
        onOpenUsage = onOpenUsage,
        onOpenHistory = onOpenHistory,
        onOpenRecipes = onOpenRecipes,
        onShare = {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, viewModel.shareText())
            }
            context.startActivity(Intent.createChooser(send, "Share week"))
        },
        onSave = { saveWeek.launch("hopa-week.txt") },
        onUnavailable = { feature ->
            Toast.makeText(
                context,
                "$feature is not on Android yet.",
                Toast.LENGTH_SHORT,
            ).show()
        },
    )
}
