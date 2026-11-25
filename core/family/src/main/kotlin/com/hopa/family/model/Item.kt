package com.hopa.family.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/** One board item. It has no visibility field: the scope it sits in is its visibility (D11). */
@Serializable
data class Item(
    val id: String,
    val type: ItemType,
    val title: String,
    val titleZh: String? = null,
    val notes: String? = null,
    val start: String? = null,
    val end: String? = null,
    val allDay: Boolean? = null,
    val forMemberIds: List<String>,
    val ownership: Ownership,
    val sensitive: Boolean,
    val importance: Importance,
    val unavailableMemberIds: List<String>,
    val parentEventId: String? = null,
    val recurrence: Recurrence? = null,
    val status: ItemStatus,
    val source: Source,
    val createdBy: String,
    val version: Int,
)

@Serializable
enum class ItemType {
    @SerialName("event")
    EVENT,

    @SerialName("preparation")
    PREPARATION,

    @SerialName("task")
    TASK,
}

@Serializable
enum class Importance {
    @SerialName("normal")
    NORMAL,

    @SerialName("critical")
    CRITICAL,
}

@Serializable
enum class ItemStatus {
    @SerialName("proposed")
    PROPOSED,

    @SerialName("open")
    OPEN,

    @SerialName("done")
    DONE,

    @SerialName("cancelled")
    CANCELLED,
}

@Serializable
data class Source(
    val kind: SourceKind,
    val label: String,
    val simulated: Boolean? = null,
)

@Serializable
enum class SourceKind {
    @SerialName("manual")
    MANUAL,

    @SerialName("chat")
    CHAT,

    @SerialName("screenshot")
    SCREENSHOT,

    @SerialName("email")
    EMAIL,

    @SerialName("group")
    GROUP,

    @SerialName("rule")
    RULE,

    @SerialName("ppma")
    PPMA,

    @SerialName("shopping")
    SHOPPING,
    ;

    /** Items from these sources wait for a person to approve them. */
    val proposes: Boolean get() = this == SCREENSHOT || this == EMAIL || this == GROUP || this == PPMA
}

/** Weekly repetition in the family's time zone. `byWeekday`: 0 = Sunday ... 6 = Saturday. */
@Serializable
data class Recurrence(
    val freq: String,
    val byWeekday: List<Int>,
    val until: String? = null,
    val exceptions: List<String>? = null,
)

/** Why an item was opened: a member is unavailable because of an event in the same scope. */
@Serializable
data class OpenReason(
    val kind: String,
    val memberId: String,
    val eventId: String,
    val date: String,
)

/** Who owns an item: nobody, "who's taking this?", or one member. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("state")
sealed interface Ownership {
    @Serializable
    @SerialName("none")
    data object None : Ownership

    @Serializable
    @SerialName("open")
    data class Open(
        val candidateIds: List<String>,
        val handedBackBy: String? = null,
        val note: String? = null,
        val reason: OpenReason? = null,
    ) : Ownership

    @Serializable
    @SerialName("assigned")
    data class Assigned(
        val ownerId: String,
        val confirmed: Boolean,
    ) : Ownership
}
