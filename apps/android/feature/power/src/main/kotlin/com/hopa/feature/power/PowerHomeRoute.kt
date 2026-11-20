package com.hopa.feature.power

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hopa.feature.power.PowerDependencies
import com.hopa.contracts.wire.RunRecord

@Composable
fun PowerHomeRoute(
    container: PowerDependencies,
    active: String,
    onSelectTab: (String) -> Unit,
    onRun: (String) -> Unit,
) {
    var recent by remember { mutableStateOf<List<RunRecord>>(emptyList()) }
    LaunchedEffect(Unit) {
        recent = container.recorder.all()
    }
    PowerHomeScreen(
        recent = recent,
        active = active,
        onSelectTab = onSelectTab,
        onRun = onRun,
    )
}
