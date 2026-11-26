package com.hopa.family.preferences

import kotlinx.serialization.Serializable

/** A family preference: hard ones (allergies, dietary laws) are never overridden; soft ones can be. */
@Serializable
data class Preference(
    val id: String,
    val aboutMemberId: String? = null,
    val text: String,
    val textZh: String? = null,
    val reason: String? = null,
    val reasonZh: String? = null,
    /** `hard` or `soft`. */
    val strength: String,
    /** Where it applies, for example `kindergarten-party`. Absent: everywhere. */
    val scope: String? = null,
    val createdBy: String? = null,
    val updatedAt: String? = null,
)

/** Who undid a change, when, and with which op. */
@Serializable
data class Undone(val at: String, val by: String, val opId: String)

/** One change to the preferences, kept so it can be undone. Its id is the op id. */
@Serializable
data class PreferenceChange(
    val id: String,
    val at: String,
    val actor: String,
    /** `added`, `corrected`, `removed` or `undone`. */
    val action: String,
    val preferenceId: String,
    /** The preference before this change; null: it did not exist. */
    val before: Preference? = null,
    val undone: Undone? = null,
)

/** The `preferences` namespace's state in the family scope. */
@Serializable
data class PreferenceState(
    val preferences: List<Preference> = emptyList(),
    val log: List<PreferenceChange> = emptyList(),
)

/** What a preference may apply to: who it is for, and the words that describe it. */
data class PreferenceContext(
    val memberIds: List<String>,
    val texts: List<String>,
    /** Extra words a caller knows (a kindergarten group passes `kindergarten`). */
    val tags: List<String> = emptyList(),
)

/** A hard preference that rules out a choice, and the word in the choice that it rules out. */
data class BlockedChoice(
    val preference: Preference,
    val term: String,
)
