package com.hopa.family.rules

import com.hopa.family.board.RejectCode
import com.hopa.family.model.FamilyRole
import com.hopa.family.model.Scope
import com.hopa.family.namespace.Namespace
import com.hopa.family.namespace.NamespaceContext
import com.hopa.family.namespace.NamespaceStep
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * The family's standing rules (Homemind's `rules.ts`, `applyRuleRequest`), as the `rules` capability:
 * in the family scope, so everyone sees them, and changed only by a parent (every rule spends money;
 * the owner's decision). A create carries the whole rule, with the action the device's own adapter
 * built; a delete keeps the rule so a restore can bring it back, once. Preparing and running a rule's
 * action is the device's (cards, ActionExecutor, the payment gate), not the board's.
 * `spec/fixtures/family/ns-rules.json` is the contract.
 */
class RulesNamespace : Namespace {
    override val name = NAME
    override val version = 1
    override val reads = emptySet<String>()

    override fun knows(kind: String, nsv: Int): Boolean = nsv == 1 && kind in KINDS

    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    override fun apply(context: NamespaceContext, op: JsonObject): NamespaceStep {
        val kind = string(op["kind"]) ?: return NamespaceStep.Stopped
        if (kind !in KINDS) return NamespaceStep.Stopped
        if (context.scope != Scope.FAMILY) return reject(RejectCode.INVALID)
        val at = string(op["at"]) ?: return reject(RejectCode.INVALID)
        val opId = string(op["opId"]) ?: return reject(RejectCode.INVALID)
        val actor = context.family.members.firstOrNull { it.id == context.actorId }
        if (actor == null || actor.role != FamilyRole.PARENT || !actor.hasAccount) return reject(RejectCode.FORBIDDEN)
        val state = context.own?.let { json.decodeFromJsonElement(RuleState.serializer(), it) } ?: RuleState()
        fun write(
            next: List<Rule>,
            action: String,
            ruleId: String,
            deleted: Rule? = null,
            log: List<RuleChange> = state.log,
        ) = NamespaceStep.Applied(
            encode(RuleState(next, log + RuleChange(opId, at, actor.id, action, ruleId, deleted))),
        )
        val unchanged = NamespaceStep.Applied(encode(state))

        when (kind) {
            "create" -> {
                if (state.rules.size >= MAX_RULES) return reject(RejectCode.CONFLICT)
                val rule = newRule(op["rule"] as? JsonObject, actor.id) ?: return reject(RejectCode.INVALID)
                if (state.rules.any { it.id == rule.id }) return reject(RejectCode.CONFLICT)
                return write(state.rules + rule, "created", rule.id)
            }
            "restore" -> {
                val target = state.log.firstOrNull { it.id == string(op["targetOpId"]) && it.action == "deleted" }
                val rule = target?.deleted ?: return reject(RejectCode.NOT_FOUND)
                if (target.undone != null) return reject(RejectCode.CONFLICT)
                if (state.rules.any { it.id == rule.id }) return reject(RejectCode.CONFLICT)
                if (state.rules.size >= MAX_RULES) return reject(RejectCode.CONFLICT)
                val marked = state.log.map {
                    if (it.id == target.id) {
                        it.copy(
                            undone = RuleUndone(at, actor.id, opId),
                        )
                    } else {
                        it
                    }
                }
                return write(state.rules + rule, "restored", rule.id, log = marked)
            }
        }
        val rule = state.rules.firstOrNull { it.id == string(op["id"]) } ?: return reject(RejectCode.NOT_FOUND)
        return when (kind) {
            "delete" -> write(state.rules.filter { it.id != rule.id }, "deleted", rule.id, deleted = rule)
            "update" -> {
                val rawTime = op["time"]
                val rawPrice = op["maxPrice"]
                if (rawTime == null && rawPrice == null) return reject(RejectCode.INVALID)
                val time = if (rawTime == null) {
                    rule.trigger.time
                } else {
                    string(rawTime)?.takeIf {
                        HHMM.matches(it)
                    } ?: return reject(RejectCode.INVALID)
                }
                val price = if (rawPrice == null) null else price(rawPrice) ?: return reject(RejectCode.INVALID)
                val sameTime = time == rule.trigger.time
                val samePrice = price == null || price.content == (rule.limits["maxPrice"] as? JsonPrimitive)?.content
                if (sameTime && samePrice) return unchanged
                val updated = rule.copy(
                    trigger = rule.trigger.copy(time = time),
                    limits = if (price == null) rule.limits else JsonObject(rule.limits + ("maxPrice" to price)),
                )
                write(state.rules.map { if (it.id == rule.id) updated else it }, "changed", rule.id)
            }
            else -> {
                val enabled = kind == "enable"
                if (rule.enabled == enabled) return unchanged
                write(
                    state.rules.map {
                        if (it.id == rule.id) it.copy(enabled = enabled) else it
                    },
                    if (enabled) "enabled" else "disabled",
                    rule.id,
                )
            }
        }
    }

    /** A rule as a create brings it, checked like Homemind's `parseRuleBody` checks a new one. */
    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    private fun newRule(o: JsonObject?, actor: String): Rule? {
        if (o == null) return null
        val id = string(o["id"])?.takeIf { ID.matches(it) } ?: return null
        val title = string(o["title"])?.takeIf { okTitle(it) }?.trim() ?: return null
        val rawZh = o["titleZh"]
        val titleZh = if (rawZh == null) null else string(rawZh)?.takeIf { okTitle(it) }?.trim() ?: return null
        val trigger = o["trigger"] as? JsonObject ?: return null
        if (string(trigger["kind"]) != "weekly") return null
        val weekday = int(trigger["weekday"])?.takeIf { it in 0..MAX_WEEKDAY } ?: return null
        val time = string(trigger["time"])?.takeIf { HHMM.matches(it) } ?: return null
        val lead = int(trigger["leadMinutes"])?.takeIf { it in MIN_LEAD..MAX_LEAD } ?: return null
        val template = o["actionTemplate"] as? JsonObject ?: return null
        val adapter = string(template["adapter"])?.takeIf { it.isNotEmpty() } ?: return null
        val payload = template["payload"] as? JsonObject ?: return null
        val limits = o["limits"] as? JsonObject ?: return null
        limits["maxPrice"]?.let { price(it) ?: return null }
        val area = string(o["area"])?.takeIf { it.isNotEmpty() } ?: return null
        return Rule(
            id, title, titleZh,
            RuleTrigger(
                "weekly",
                weekday,
                time,
                lead,
            ),
            ActionTemplate(adapter, payload), limits, area, true, actor,
        )
    }

    companion object {
        const val NAME = "rules"
        val KINDS = setOf("create", "update", "enable", "disable", "delete", "restore")
        const val MAX_RULES = 20
        const val MAX_TITLE = 80
        const val MIN_LEAD = 5
        const val MAX_LEAD = 24 * 60
        const val MAX_PRICE = 5000.0
        private const val MAX_WEEKDAY = 6
        private val ID = Regex("^[a-z0-9][a-z0-9_-]{0,63}$")
        private val HHMM = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
        private val CONTROL = Regex("[\\u0000-\\u001f\\u007f]")

        internal val json = Json {
            ignoreUnknownKeys = false
            encodeDefaults = false
            explicitNulls = false
        }

        fun encode(state: RuleState): JsonObject = json.encodeToJsonElement(RuleState.serializer(), state) as JsonObject

        /** The rules stored in a scope's partition (none before the first op). */
        fun of(own: JsonObject?): List<Rule> =
            own?.let { json.decodeFromJsonElement(RuleState.serializer(), it).rules }.orEmpty()

        private fun reject(code: RejectCode): NamespaceStep = NamespaceStep.Rejected(code)

        private fun string(x: JsonElement?): String? = (x as? JsonPrimitive)?.takeIf { it.isString }?.content

        private fun okTitle(v: String) = v.trim().isNotEmpty() && v.length <= MAX_TITLE && !CONTROL.containsMatchIn(v)

        private fun int(x: JsonElement?): Int? {
            val n = (x as? JsonPrimitive)?.takeIf { !it.isString && it !is JsonNull }?.doubleOrNull ?: return null
            return if (n == Math.floor(n)) n.toInt() else null
        }

        /** A price from 1 to [MAX_PRICE], kept as the number it was written as. */
        private fun price(x: JsonElement): JsonPrimitive? {
            val p = (x as? JsonPrimitive)?.takeIf { !it.isString && it !is JsonNull } ?: return null
            val n = p.doubleOrNull ?: return null
            return p.takeIf { n.isFinite() && n >= 1 && n <= MAX_PRICE }
        }
    }
}
