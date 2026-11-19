package com.hopa.catalog.routing

data class RouteMatch(
    val target: RouteTarget?,
    val score: Int,
    val term: String,
)
