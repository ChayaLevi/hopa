package com.hopa.family.intents

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * One chat message, as the model read it, after validation (Homemind's `ChatIntent`). Every field
 * was built by the validator from known fields only; every id is one the member may see. Turning an
 * intent into board ops (with the actor from the device's member, and ids and time minted then) is
 * the engine's job, and a rule from chat is never live: it becomes a card that needs a tap.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("intent")
sealed interface ChatIntent {
    /** A question about the board; [itemIds] are the items the answer is about. */
    @Serializable
    @SerialName("ask")
    data class Ask(val answer: String, val itemIds: List<String>) : ChatIntent

    @Serializable
    @SerialName("create_event")
    data class CreateEvent(
        val title: String,
        val start: String,
        val end: String? = null,
        val allDay: Boolean,
        val forMemberIds: List<String>,
        val unavailableMemberIds: List<String>,
        val visibility: String,
        val importance: String,
    ) : ChatIntent

    @Serializable
    @SerialName("create_task")
    data class CreateTask(
        val title: String,
        val due: String? = null,
        val ownerId: String? = null,
        val forMemberIds: List<String>,
    ) : ChatIntent

    @Serializable
    @SerialName("add_preparation")
    data class AddPreparation(val eventId: String, val title: String, val due: String? = null) : ChatIntent

    @Serializable
    @SerialName("assign")
    data class Assign(val itemId: String, val memberId: String) : ChatIntent

    @Serializable
    @SerialName("claim")
    data class Claim(val itemId: String) : ChatIntent

    @Serializable
    @SerialName("mark_done")
    data class MarkDone(val itemId: String) : ChatIntent

    @Serializable
    @SerialName("update_item")
    data class UpdateItem(val itemId: String, val title: String? = null, val start: String? = null) : ChatIntent

    @Serializable
    @SerialName("split_item")
    data class SplitItem(val itemId: String, val parts: List<Part>) : ChatIntent {
        @Serializable
        data class Part(val title: String, val due: String? = null)
    }

    @Serializable
    @SerialName("delete_item")
    data class DeleteItem(val itemId: String) : ChatIntent

    @Serializable
    @SerialName("add_preference")
    data class AddPreference(
        val text: String,
        val reason: String? = null,
        val aboutMemberId: String? = null,
        val strength: String,
        val scope: String? = null,
    ) : ChatIntent

    @Serializable
    @SerialName("add_to_shopping")
    data class AddToShopping(val items: List<Entry>) : ChatIntent {
        @Serializable
        data class Entry(val storeId: String, val name: String, val qty: Int? = null, val note: String? = null)
    }

    @Serializable
    @SerialName("create_rule")
    data class CreateRule(
        val title: String,
        val weekday: Int,
        val time: String,
        val leadMinutes: Int,
        val action: String,
    ) : ChatIntent

    @Serializable
    @SerialName("report_situation")
    data class ReportSituation(
        val situation: String,
        val memberId: String? = null,
        val date: String,
        val summary: String,
    ) : ChatIntent

    @Serializable
    @SerialName("why")
    data class Why(val itemId: String) : ChatIntent

    companion object {
        val NAMES = listOf(
            "ask", "create_event", "create_task", "add_preparation", "assign", "claim", "mark_done", "update_item",
            "split_item", "delete_item", "add_preference", "add_to_shopping", "create_rule", "report_situation", "why",
        )
        val SITUATIONS = listOf("sick", "away", "late", "other")
    }
}
