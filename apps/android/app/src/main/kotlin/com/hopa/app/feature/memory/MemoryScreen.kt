package com.hopa.app.feature.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.app.ui.component.HopaTopBar
import com.hopa.app.ui.theme.HopaColor

/**
 * The facts HOPA carries with every request. Skills have their own page: a fact
 * is context, a skill is a procedure, and mixing both on one scroll made two
 * different cards land on the same wall of text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    state: MemoryUiState,
    onBack: () -> Unit,
    onAddFact: (String) -> Unit,
    onDeleteFact: (String) -> Unit,
    onClearFacts: () -> Unit,
    onExport: () -> Unit,
) {
    var fact by remember { mutableStateOf("") }

    Scaffold(
        containerColor = HopaColor.Bg,
        topBar = {
            HopaTopBar {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = HopaColor.TextPrimary,
                    )
                }
                Text(
                    "Memory",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = HopaColor.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (state.facts.isNotEmpty()) {
                    TextButton(onClick = onExport) { Text("Export") }
                    TextButton(onClick = onClearFacts) { Text("Clear") }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Facts HOPA keeps with every request. Add one, delete one, or clear them all.",
                fontSize = 12.sp,
                color = HopaColor.TextHint,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = fact,
                    onValueChange = { fact = it },
                    label = { Text("A fact to remember") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        onAddFact(fact)
                        fact = ""
                    },
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("Add") }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HopaColor.Surface, RoundedCornerShape(12.dp))
                    .border(1.dp, HopaColor.Divider, RoundedCornerShape(12.dp)),
            ) {
                if (state.facts.isEmpty()) {
                    Text(
                        "Nothing saved yet.",
                        fontSize = 12.sp,
                        color = HopaColor.TextHint,
                        modifier = Modifier.padding(14.dp),
                    )
                } else {
                    state.facts.forEach { saved ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                saved,
                                fontSize = 13.sp,
                                color = HopaColor.TextPrimary,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 10.dp),
                            )
                            IconButton(onClick = { onDeleteFact(saved) }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Delete fact",
                                    tint = HopaColor.TextHint,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
