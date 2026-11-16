package com.hopa.app.feature.browser

import androidx.compose.runtime.Composable

/** The in-app browser; [initialUrl] is already loaded when it opens. */
@Composable
fun BrowserRoute(
    initialUrl: String = "",
    onBack: () -> Unit,
) {
    BrowserScreen(initialUrl = initialUrl, onBack = onBack)
}
