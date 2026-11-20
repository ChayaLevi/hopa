package com.hopa.contracts.task

/** One background task the person started while another was already running. */
data class TaskItem(
    val id: String,
    val goal: String,
    val conversationId: String,
    val state: String,
    val detail: String = "",
)
