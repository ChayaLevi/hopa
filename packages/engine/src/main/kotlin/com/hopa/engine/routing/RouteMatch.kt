package com.hopa.engine.routing

data class RouteMatch(
    val target: RouteTarget?,
    val score: Int,
    val term: String,
)
