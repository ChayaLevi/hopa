package com.hopa.feature.runs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hopa.feature.runs.RunsDependencies
import com.hopa.contracts.wire.RunRecord

@Composable
fun RunsRoute(
    container: RunsDependencies,
    onBack: () -> Unit,
) {
    var records by remember { mutableStateOf<List<RunRecord>>(emptyList()) }
    LaunchedEffect(Unit) {
        records = container.recorder.all()
    }
    RunsScreen(
        records = records,
        onBack = onBack,
        onVerify = { runId -> container.recorder.verify(runId) },
        onClear = {
            container.recorder.clear()
            records = emptyList()
        },
    )
}
