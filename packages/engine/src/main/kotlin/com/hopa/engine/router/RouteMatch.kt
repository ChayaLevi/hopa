package com.hopa.engine.router

data class RouteMatch(
    val target: RouteTarget?,
    val score: Int,
    val term: String,
)
