package com.hopa.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hopa.app.di.AppContainer
import com.hopa.app.navigation.HopaNavHost
import com.hopa.ui.component.ApprovalSheet
import com.hopa.ui.component.MascotAppearance
import com.hopa.ui.theme.HopaTheme

@Composable
fun HopaApp(container: AppContainer, initialGoal: String = "") {
    val question by container.approval.question.collectAsStateWithLifecycle()
    // Null until the stored value arrives, so the mascot does not flash the
    // default look for one frame when a person prefers the framed one.
    val settings by container.settings.settings.collectAsState(initial = null)
    LaunchedEffect(settings?.mascotFramed) {
        settings?.let { MascotAppearance.framed = it.mascotFramed }
    }
    HopaTheme {
        HopaNavHost(container = container, initialGoal = initialGoal)
        // One sheet for every tab: a remote run may ask while the person is on
        // Running apps or My computer, not only in chat.
        question?.let { open ->
            ApprovalSheet(
                request = open.request,
                card = open.card,
                onDeny = { container.approval.resolve(false) },
                onAllow = { tap -> container.approval.resolve(true, tap) },
            )
        }
    }
}
