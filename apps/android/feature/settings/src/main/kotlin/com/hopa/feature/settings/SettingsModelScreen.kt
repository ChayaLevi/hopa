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
import com.hopa.inference.choice.ProviderState
import com.hopa.ui.settings.SettingsPage
import com.hopa.ui.theme.HopaColor

/** Where answers come from: provider, endpoint, model, key. */
@Composable
fun SettingsModelScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onSave: (String, String, String, String, Boolean, String, String, Boolean) -> Unit,
    onTestModel: (String, String, String, String, Boolean, String, String, Boolean) -> Unit,
    onDeviceModels: @Composable () -> Unit = {},
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

    val shownState = if (provider == "on-device") ProviderState.ON_DEVICE else state.providerState
    SettingsPage(title = "Answers", onBack = onBack) {
        Text(
            "Answers come from ${providerLabel(provider)}. " +
                "Choose where they come from on How HOPA acts.",
            style = MaterialTheme.typography.bodySmall,
            color = HopaColor.TextHint,
        )
        if (provider == "stub") {
            Text(
                "Demo answers with a small canned set: no model, no network, no key.",
                style = MaterialTheme.typography.bodySmall,
                color = HopaColor.TextHint,
            )
        }
        if (provider == "on-device") {
            Text(
                "Answers come from a model file on this phone; nothing is sent to a server.",
                style = MaterialTheme.typography.bodySmall,
                color = HopaColor.TextHint,
            )
            onDeviceModels()
        }
        if (provider != "on-device" && provider != "stub") {
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
        }
        Text("Provider state: ${providerSummary(state.copy(providerState = shownState)).lowercase()}", fontSize = 12.sp)
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
            if (provider != "on-device" && provider != "stub") {
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

/** The provider as a person reads it, not the stored wire name. */
private fun providerLabel(provider: String): String = when (provider) {
    "stub" -> "Demo"
    "on-device" -> "On-device"
    else -> "Server"
}
