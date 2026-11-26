package com.hopa.family.summary

/** Why an item is under "What you need to do". */
enum class ToDoReason(val wire: String) {
    /** Theirs, confirmed. */
    OWNER("owner"),

    /** Given to them, not confirmed yet. */
    UNCONFIRMED("unconfirmed"),

    /** "Who is taking this?" and they could. */
    ASKED("asked"),

    /** A reminder that reached them got no answer. */
    UNANSWERED("unanswered"),
}
