package com.hopa.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable

/** Cross-fade between two scenes when the chosen mode changes. */
@Composable
fun ModeSceneSwitch(mode: String) {
    AnimatedContent(
        targetState = mode,
        transitionSpec = {
            (fadeIn() + scaleIn(initialScale = 0.98f)) togetherWith
                (fadeOut() + scaleOut(targetScale = 0.98f))
        },
        label = "mode-scene",
    ) { current ->
        ModeScene(current)
    }
}
