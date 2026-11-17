package com.hopa.app.feature.memory

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hopa.app.di.AppContainer

@Composable
fun SkillsRoute(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel: MemoryViewModel = viewModel(factory = MemoryViewModelFactory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    SkillsScreen(
        state = state,
        onBack = onBack,
        onAddSkill = viewModel::addSkill,
        onDeleteSkill = viewModel::deleteSkill,
        onClearSkills = viewModel::clearSkills,
        onExport = {
            val text = buildString {
                appendLine("HOPA skills")
                state.skills.forEach { appendLine("- \"${it.trigger}\" → ${it.action}") }
            }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(send, "Export skills"))
        },
    )
}
