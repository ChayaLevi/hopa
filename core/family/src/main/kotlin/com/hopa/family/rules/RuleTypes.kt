package com.hopa.family.rules

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** When a weekly rule prepares its action: `leadMinutes` before `time` (local) on `weekday` (0 = Sunday). */
@Serializable
data class RuleTrigger(
    val kind: String,
    val weekday: Int,
    val time: String,
    val leadMinutes: Int,
)

/** The action a rule prepares, as the device's adapter built it (for example a pizza order). */
@Serializable
data class ActionTemplate(
    val adapter: String,
    val payload: JsonObject,
)

/**
 * A standing rule. It never acts: it prepares an action that waits for a parent's tap, and the
 * action runs through `ActionExecutor` on the acting device (D7), with the payment gate.
 */
@Serializable
data class Rule(
    val id: String,
    val title: String,
    val titleZh: String? = null,
    val trigger: RuleTrigger,
    val actionTemplate: ActionTemplate,
    /** `maxPrice`, `currency`: kept as written, so a price reads back exactly. */
    val limits: JsonObject,
    val area: String,
    val enabled: Boolean,
    val createdBy: String,
)

/** Who undid a change, when, and with which op. */
@Serializable
data class RuleUndone(val at: String, val by: String, val opId: String)

/** One change to the rules. A delete keeps the rule, so a restore can bring it back. Its id is the op id. */
@Serializable
data class RuleChange(
    val id: String,
    val at: String,
    val actor: String,
    /** created, changed, enabled, disabled, deleted or restored. */
    val action: String,
    val ruleId: String,
    val deleted: Rule? = null,
    val undone: RuleUndone? = null,
)

/** The `rules` namespace's state in the family scope. */
@Serializable
data class RuleState(
    val rules: List<Rule> = emptyList(),
    val log: List<RuleChange> = emptyList(),
)
