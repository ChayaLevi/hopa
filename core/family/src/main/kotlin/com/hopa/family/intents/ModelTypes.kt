package com.hopa.family.intents

/** One earlier turn of the same call, for the one retry ("your reply was not valid because..."). */
data class ModelTurn(
    /** `assistant` or `user`. */
    val role: String,
    val content: String,
)

/** What the model is asked: the instructions, the user text with its data blocks, and a size cap. */
data class ModelRequest(
    /** For logs only: `intent`, `extract`. */
    val purpose: String,
    val system: String,
    val user: String,
    val maxTokens: Int,
    val followUp: List<ModelTurn>? = null,
)

enum class ModelErrorCode(val wire: String) {
    NOT_CONFIGURED("not_configured"),
    TIMEOUT("timeout"),
    NETWORK("network"),
    HTTP("http"),
    AUTH("auth"),
    EMPTY("empty"),
}

sealed interface ModelResult {
    data class Ok(val content: String) : ModelResult

    data class Failed(val code: ModelErrorCode, val message: String) : ModelResult
}

/** The member's language for any text the model writes. */
enum class Lang(val wire: String) {
    EN("en"),
    ZH("zh"),
}

/** One line of the member's recent chat with Homemind, oldest first. Kept on the device. */
data class ChatLine(
    /** `member` or `homemind`. */
    val from: String,
    val text: String,
)

/** A store the family shops at, and what is already waiting there. */
data class IntentStore(
    val id: String,
    val name: String,
    val nameZh: String? = null,
    val waiting: List<String>,
)

/** A preference as the prompt shows it. */
data class PromptPreference(
    val about: String? = null,
    val text: String,
    val reason: String? = null,
    val strength: String,
    val scope: String? = null,
)
