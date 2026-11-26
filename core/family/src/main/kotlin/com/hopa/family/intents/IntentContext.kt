package com.hopa.family.intents

/** What a reply is checked against. Built from the member's view, so it holds nothing hidden. */
data class IntentContext(
    val memberId: String,
    val isParent: Boolean,
    val nowMs: Long,
    val timezone: String,
    val memberIds: List<String>,
    val itemIds: List<String>,
    val eventIds: List<String>,
    /** Item titles by id: an id the model writes into an answer is replaced with the title. */
    val titles: Map<String, String>,
    /** The family's stores (shopping lists). Empty: no list to add to. */
    val storeIds: List<String>,
)

/** A checked model reply: the value, or what was wrong, word for word for the retry. */
sealed interface Validation<out T> {
    data class Ok<T>(val value: T) : Validation<T>

    data class Invalid(val problems: List<String>) : Validation<Nothing>
}
