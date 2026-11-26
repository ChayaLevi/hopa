package com.hopa.family.judgment

/** Why a message got its tier. Screens show the reason by this code, never a model's words. */
enum class JudgmentReason(val wire: String) {
    URGENT_WORDS("urgent-words"),
    SIGN_UP("sign-up"),
    NOT_URGENT("not-urgent"),
    SOON("soon"),
    NEXT_WEEK("next-week"),
    NO_DATE("no-date"),
}
