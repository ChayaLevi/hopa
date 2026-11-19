package com.hopa.catalog

data class RouteMatch(
    val target: RouteTarget?,
    val score: Int,
    val term: String,
)
