package com.hopa.family.reminders

data class Routed(
    val send: List<Notification>,
    val held: List<HeldAlert>,
)
