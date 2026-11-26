package com.hopa.family.reminders

/** What a reminder asks of its recipients. */
enum class ReminderAsk(val wire: String) {
    /** Assigned and confirmed: one note before it starts, to the owner. */
    HEADS_UP("heads-up"),

    /** Assigned, not confirmed yet: "Are you on it?", to the owner. */
    CONFIRM("confirm"),

    /** "Who is taking this?": to the candidates (an unowned item only when it is critical). */
    OPEN("open"),
}

/** How an alert may interrupt. `URGENT` breaks quiet hours and is never batched. */
enum class Tier(val wire: String) {
    URGENT("urgent"),
    TIMED("timed"),
}

enum class AlertKind(val wire: String) {
    /** An attempt to the reminder's own recipients. */
    REMINDER("reminder"),

    /** No answer: the other parent, or both parents, are told. */
    ESCALATION("escalation"),

    /** No answer after the escalation: the parents learn the family's backup contact was messaged. */
    BACKUP("backup"),
}
