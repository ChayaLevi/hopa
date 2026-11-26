package com.hopa.family.summary

enum class SummaryKind(val wire: String) {
    /** The evening summary: what is left of that day and the next. */
    DAILY("daily"),

    /** The week ahead: the seven days after, sent on the evening before the family's week starts. */
    WEEKLY("weekly"),
}
