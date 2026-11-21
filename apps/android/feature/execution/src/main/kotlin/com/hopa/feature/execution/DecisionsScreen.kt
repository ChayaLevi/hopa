package com.hopa.feature.execution

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.feature.settings.SettingsChoiceCard
import com.hopa.feature.settings.SettingsDivider
import com.hopa.feature.settings.SettingsGroup
import com.hopa.feature.settings.SettingsHeader
import com.hopa.feature.settings.SettingsSwitchRow
import com.hopa.feature.settings.SettingsUiState
import com.hopa.ui.settings.SettingsPage
import com.hopa.ui.theme.HopaColor

/**
 * Which layer decides a step, which ones only watch, and what is active now.
 *
 * It lives with the Act screens because it is part of how HOPA acts: the
 * choices here change who taps and who asks, not how a chat looks.
 *
 * The page never claims more than the registry can back: a shadow layer says it
 * does not decide, and the "Active now" rows name the layer per action class,
 * including "Model" when no layer is allowed there.
 */
@Composable
fun DecisionsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onMode: (String) -> Unit,
    onShadow: (Boolean) -> Unit,
    onInstantCommands: (Boolean) -> Unit,
) {
    val settings = state.settings
    SettingsPage(title = "Decisions", onBack = onBack) {
        SettingsHeader("Decisions", "Who chooses the next step.")

        SettingsChoiceCard(
            title = "Off",
            description = "The model decides every step, exactly as before.",
            howTo = "Nothing changes. The decision layers are not asked.",
            selected = settings.decisionMode == "off",
            onSelect = { onMode("off") },
        )
        SettingsChoiceCard(
            title = "Automatic",
            description = "The best allowed layer for each class of action.",
            howTo = "Reading the screen follows the rules. A change and a payment keep " +
                "the model and the approval step.",
            selected = settings.decisionMode == "auto",
            onSelect = { onMode("auto") },
        )
        state.decisionLayers.forEach { layer ->
            SettingsChoiceCard(
                title = layer.title,
                description = layer.summary,
                howTo = "${layer.detail}. ${layer.howTo}",
                selected = settings.decisionMode == layer.id,
                onSelect = { onMode(layer.id) },
            )
        }

        SectionLabel("Recording")
        SettingsGroup {
            SettingsSwitchRow(
                title = "Record what other layers would decide",
                checked = settings.shadowDecisions,
                onChange = onShadow,
            )
            SettingsDivider()
            SettingsSwitchRow(
                title = "Instant commands",
                checked = settings.instantCommands,
                onChange = onInstantCommands,
            )
        }
        Text(
            "Instant commands run open, back, home, scroll and wait without a model. " +
                "Anything they do not recognize goes to the model. " +
                "Steps (Auto / Model / Graph) are chosen on the Act page.",
            fontSize = 12.sp,
            color = HopaColor.TextHint,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        SectionLabel("Active now")
        SettingsGroup {
            ActiveRow("Reading the screen", state.decisionActive["read"])
            SettingsDivider()
            ActiveRow("Changing a setting", state.decisionActive["change"])
            SettingsDivider()
            ActiveRow("Payments and deletes", state.decisionActive["dangerous"])
        }
        Text(
            "A change or a payment still asks for approval, whatever is selected here.",
            fontSize = 12.sp,
            color = HopaColor.TextHint,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = HopaColor.TextHint,
    )
}

@Composable
private fun ActiveRow(label: String, layerId: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            color = HopaColor.TextPrimary,
        )
        Text(
            layerName(layerId),
            fontSize = 13.sp,
            color = HopaColor.TextSecondary,
        )
    }
}

private fun layerName(layerId: String?): String = when (layerId) {
    null, "", "model" -> "Model"
    "rules" -> "Rules"
    "laix" -> "LAIX"
    else -> layerId
}
