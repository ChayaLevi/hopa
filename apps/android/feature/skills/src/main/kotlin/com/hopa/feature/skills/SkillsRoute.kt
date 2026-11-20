package com.hopa.feature.skills

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SkillsRoute(
    onBack: () -> Unit,
) {
    val viewModel: SkillsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    SkillsScreen(
        state = state,
        onBack = onBack,
        onAddSkill = viewModel::add,
        onDeleteSkill = viewModel::delete,
        onClearSkills = viewModel::clear,
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
