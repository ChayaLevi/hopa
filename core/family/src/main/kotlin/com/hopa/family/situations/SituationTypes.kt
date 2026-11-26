package com.hopa.family.situations

import com.hopa.family.model.Scope

/** "Noa is sick on <date>": who, and which local day. The model only reads this from the message. */
data class SituationReport(
    val memberId: String,
    val date: String,
)

enum class ProposalKind(val wire: String) {
    /** A pickup or drop-off: not needed when the child stays home. */
    CANCEL("cancel"),

    /** Any other event for that member, to the same local time a week later. */
    POSTPONE("postpone"),

    /** One occurrence of a weekly event. */
    SKIP("skip"),

    /** Something the sick member owned that day becomes "Who is taking this?". */
    OPEN("open"),
}

/** One suggestion, for an item in [scope]. Applying it checks the item did not move since ([start]). */
data class SituationProposal(
    val id: String,
    val kind: ProposalKind,
    val scope: Scope,
    val itemId: String,
    val start: String? = null,
    val newStart: String? = null,
    val newEnd: String? = null,
    val date: String? = null,
    val candidateIds: List<String>? = null,
)

/** A parent marked away that day, by an event the reporter may see. */
data class Away(
    val memberId: String,
    val eventId: String,
    val start: String,
    val end: String,
)

/** What falls through that day and what to suggest, from the reporter's view. */
data class SituationPlan(
    val proposals: List<SituationProposal>,
    /** Parents with an account, other than the sick member, not away that day. */
    val available: List<String>,
    val away: List<Away>,
    /** Parents with an account, other than the sick member. */
    val parents: List<String>,
)

/** Why a report or a suggestion was refused. Screens show it by its code. */
enum class SituationRefusal(val wire: String) {
    FORBIDDEN("forbidden"),
    NOT_FOUND("not_found"),
    PAST("past"),
    TOO_FAR("too_far"),

    /** The item is no longer on the board. */
    GONE("gone"),

    /** The item moved, or someone already asked who takes it, since the suggestion. */
    CHANGED("changed"),
}
