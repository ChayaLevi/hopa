package com.hopa.ui.a2ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hopa.contracts.a2ui.A2uiComponent
import com.hopa.contracts.a2ui.A2uiCodec
import com.hopa.contracts.a2ui.A2uiOutcome
import com.hopa.contracts.a2ui.A2uiSession
import com.hopa.contracts.a2ui.A2uiSurface
import com.hopa.contracts.a2ui.ChoiceSurfaces
import java.time.Instant

/**
 * The answers to a choice, as chips under the conversation. They come from a
 * choice surface (ADR-0031): the question is already in the chat, so only the
 * option buttons are drawn, and a tap is read back with
 * [ChoiceSurfaces.chosen]. Options a card cannot carry (more than eight,
 * repeats, or too long) are shown as plain chips, as before.
 */
@Composable
fun ChoiceChips(
    options: List<String>,
    onOption: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val surface = remember(options) { choiceSurface(options) }
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (surface == null) {
            options.forEach { option -> AssistChip(onClick = { onOption(option) }, label = { Text(option) }) }
            return@Row
        }
        val content = surface.component("content") as A2uiComponent.Column
        surface.children(content).filterIsInstance<A2uiComponent.Button>().forEach { button ->
            val label = surface.component(button.child) as A2uiComponent.Text
            AssistChip(
                onClick = {
                    surface.tap(button.id, Instant.now())
                        ?.let { ChoiceSurfaces.chosen(it, surface.id, options) }
                        ?.let(onOption)
                },
                label = { Text(surface.text(label.text)) },
            )
        }
    }
}

private fun choiceSurface(options: List<String>): A2uiSurface? {
    if (options.isEmpty() || options.size > ChoiceSurfaces.MAX_OPTIONS || options.toSet().size != options.size) {
        return null
    }
    val surfaceId = ChoiceSurfaces.surfaceId("chat", 1)
    // The question is the assistant's message above the chips; the card only needs the options.
    val message = ChoiceSurfaces.build(question = "", options = options, surfaceId = surfaceId).single()
    return (A2uiSession().apply(A2uiCodec.encode(message)) as? A2uiOutcome.Ready)?.surface
}
