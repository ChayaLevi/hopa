package com.hopa.engine.contracts

import kotlinx.serialization.Serializable

@Serializable
data class ScreenSize(
    val width: Int,
    val height: Int,
)
