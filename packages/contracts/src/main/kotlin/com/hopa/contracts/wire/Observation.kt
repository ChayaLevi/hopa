package com.hopa.contracts.wire

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Observation(
    @SerialName("schemaVersion") val schemaVersion: String = "1",
    val id: String,
    val timestamp: Long,
    @SerialName("displayId") val displayId: Int = 0,
    @SerialName("windowId") val windowId: String = "active",
    val rotation: Int = 0,
    val density: Float = 1.0f,
    @SerialName("screenSize") val screenSize: ScreenSize,
    val focus: String = "",
    val text: String = "",
    val tree: ObservationNode,
    @SerialName("screenshotRef") val screenshotRef: String = "",
    val app: String = "",
    val hash: String = "",
    val truncated: Boolean = false,
)

@Serializable
data class ObservationNode(
    val ref: String = "",
    val type: String = "",
    val text: String = "",
    val description: String = "",
    val id: String = "",
    val bounds: List<Int>? = null,
    val clickable: Boolean = false,
    val enabled: Boolean = true,
    val checked: Boolean = false,
    val scrollable: Boolean = false,
    val visible: Boolean = true,
    val focused: Boolean = false,
    val children: List<ObservationNode> = emptyList(),
)

@Serializable
data class ScreenSize(
    val width: Int,
    val height: Int,
)
