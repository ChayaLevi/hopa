package com.hopa.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.ui.settings.SettingsPage
import com.hopa.ui.settings.SettingsSegment
import com.hopa.ui.theme.HopaColor

/** Where answers come from: provider, endpoint, model, key. */
@Composable
fun SettingsModelScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onSave: (String, String, String, String, Boolean, String, String, Boolean) -> Unit,
    onTestModel: (String, String, String, String, Boolean, String, String, Boolean) -> Unit,
) {
    var provider by rememberSaveable { mutableStateOf(state.settings.provider) }
    var endpoint by rememberSaveable { mutableStateOf(state.settings.endpoint) }
    var model by rememberSaveable { mutableStateOf(state.settings.model) }
    var key by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.settings, state.key) {
        provider = state.settings.provider
        endpoint = state.settings.endpoint
        model = state.settings.model
        if (key.isEmpty()) {
            key = state.key
        }
    }

    SettingsPage(title = "Answers", onBack = onBack) {
        SettingsSegment(
            options = listOf(
                "stub" to "Demo",
                "openai" to "Cloud",
                "lan" to "LAN",
            ),
            selected = when (provider) {
                "stub" -> "stub"
                "lan" -> "lan"
                else -> "openai"
            },
            onSelect = { value ->
                provider = value
                endpoint = when (value) {
                    "lan" -> "http://192.168.1.5:11434/v1"
                    "openai" -> "https://api.deepseek.com/v1"
                    else -> endpoint
                }
            },
        )
        OutlinedTextField(
            value = endpoint,
            onValueChange = { endpoint = it },
            label = { Text("Endpoint") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text("Model") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text("API key (stored in Keystore)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            if (state.keySaved) "A key is saved on this phone" else "No key saved yet",
            style = MaterialTheme.typography.bodySmall,
        )
        Text("Provider state: ${providerSummary(state).lowercase()}", fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onSave(
                        provider,
                        endpoint,
                        model,
                        key,
                        state.settings.notifyAnswers,
                        state.settings.bridgeUrl,
                        "",
                        state.settings.useBridge,
                    )
                },
                enabled = !state.busy,
            ) {
                Text(if (state.busy) "Saving\u2026" else "Save")
            }
            Button(
                onClick = {
                    onTestModel(
                        provider,
                        endpoint,
                        model,
                        key,
                        state.settings.notifyAnswers,
                        state.settings.bridgeUrl,
                        "",
                        state.settings.useBridge,
                    )
                },
                enabled = !state.busy,
            ) {
                Text("Save and test")
            }
        }
        if (state.saved) {
            Text(state.notice ?: "Saved", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        }
        state.modelTest?.let { result ->
            Text(
                result,
                fontSize = 12.sp,
                color = if (result.startsWith("Model answered")) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
        Text(
            "LAN works without internet when the endpoint is an OpenAI-compatible " +
                "server on the same network.",
            style = MaterialTheme.typography.bodySmall,
            color = HopaColor.TextHint,
        )
    }
}
