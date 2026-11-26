package com.hopa.family.preferences

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

/**
 * The family's preferences (Homemind's `preferences.ts`), as the `preferences` capability, in the
 * family scope only (the owner's decision). Anyone with an account adds one; only a parent corrects
 * or removes a hard one, or turns a soft one hard and back. Saving the same words for the same person
 * and scope corrects the saved one instead of adding a twin. Every change can be undone by its author
 * or a parent, once, and only the latest change to that preference. Ids come in the op (D10).
 * `spec/fixtures/family/ns-preferences.json` is the contract.
 */
class PreferencesNamespace : Namespace {
    override val name = NAME
    override val version = 1
    override val reads = emptySet<String>()

    override fun knows(kind: String, nsv: Int): Boolean = nsv == 1 && kind in KINDS

    @Suppress("ReturnCount")
    override fun apply(context: NamespaceContext, op: JsonObject): NamespaceStep {
        val kind = string(op["kind"]) ?: return NamespaceStep.Stopped
        if (kind !in KINDS) return NamespaceStep.Stopped
        if (context.scope != Scope.FAMILY) return reject(RejectCode.INVALID)
        val at = string(op["at"]) ?: return reject(RejectCode.INVALID)
        val opId = string(op["opId"]) ?: return reject(RejectCode.INVALID)
        val state = context.own?.let { json.decodeFromJsonElement(PreferenceState.serializer(), it) }
            ?: PreferenceState()
        val actor = context.family.members.firstOrNull { it.id == context.actorId }
            ?: return reject(RejectCode.NOT_FOUND)
        val isParent = actor.role == FamilyRole.PARENT
        val change = Writer(state, opId, at, actor.id)
        return when (kind) {
            "remove" -> {
                val old = state.preferences.firstOrNull { it.id == string(op["id"]) }
                    ?: return reject(RejectCode.NOT_FOUND)
                if (old.strength == HARD && !isParent) return reject(RejectCode.FORBIDDEN)
                change.write("removed", old.id, old, state.preferences.filter { it.id != old.id })
            }
            "save" -> save(op, state, context, isParent, change)
            "undo" -> undo(string(op["targetOpId"]), state, actor.id, isParent, change)
            else -> NamespaceStep.Stopped
        }
    }

    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    private fun save(
        op: JsonObject,
        state: PreferenceState,
        context: NamespaceContext,
        isParent: Boolean,
        change: Writer,
    ): NamespaceStep {
        val input = input(op, context) ?: return reject(RejectCode.INVALID)
        val requestId = string(op["id"])
        val old = if (requestId != null) {
            state.preferences.firstOrNull { it.id == requestId } ?: return reject(RejectCode.NOT_FOUND)
        } else {
            state.preferences.firstOrNull {
                (it.aboutMemberId ?: "") == (input.aboutMemberId ?: "") && (it.scope ?: "") == (input.scope ?: "") &&
                    (norm(it.text) == norm(input.text) || (it.textZh != null && norm(it.textZh) == norm(input.text)))
            }
        }
        if (old == null) {
            if (state.preferences.size >= MAX_COUNT) return reject(RejectCode.CONFLICT)
            val newId = string(op["newId"])?.takeIf { ID.matches(it) } ?: return reject(RejectCode.INVALID)
            if (state.preferences.any { it.id == newId }) return reject(RejectCode.CONFLICT)
            val preference = input.copy(id = newId, createdBy = change.actor, updatedAt = change.at)
            return change.write("added", newId, null, state.preferences + preference)
        }
        if ((old.strength == HARD || input.strength == HARD) && old.strength != input.strength && !isParent) {
            return reject(RejectCode.FORBIDDEN)
        }
        val keepZh = old.textZh != null && (norm(old.text) == norm(input.text) || norm(old.textZh) == norm(input.text))
        var merged = Preference(
            id = old.id,
            aboutMemberId = input.aboutMemberId,
            text = input.text,
            textZh = old.textZh.takeIf { keepZh },
            reason = input.reason ?: if (requestId != null) null else old.reason,
            strength = input.strength,
            scope = input.scope,
            createdBy = old.createdBy,
            updatedAt = change.at,
        )
        // Saving the seeded Chinese words keeps the English ones.
        if (old.textZh != null && norm(old.textZh) == norm(input.text)) merged = merged.copy(text = old.text)
        if (old.reasonZh != null && merged.reason == old.reason) merged = merged.copy(reasonZh = old.reasonZh)
        if (old.strength == HARD && !isParent && !same(old, merged)) return reject(RejectCode.FORBIDDEN)
        if (same(old, merged)) return NamespaceStep.Applied(encode(state))
        return change.write("corrected", old.id, old, state.preferences.map { if (it.id == old.id) merged else it })
    }

    @Suppress("ReturnCount")
    private fun undo(
        targetOpId: String?,
        state: PreferenceState,
        actor: String,
        isParent: Boolean,
        change: Writer,
    ): NamespaceStep {
        val target = state.log.firstOrNull { it.id == targetOpId } ?: return reject(RejectCode.NOT_FOUND)
        if (target.undone != null) return reject(RejectCode.CONFLICT)
        if (target.actor != actor && !isParent) return reject(RejectCode.FORBIDDEN)
        // Only the latest change to that preference, so an older Undo can't wipe a newer correction.
        val later = state.log.drop(state.log.indexOf(target) + 1).any {
            it.preferenceId == target.preferenceId && it.undone == null
        }
        if (later) return reject(RejectCode.CONFLICT)
        val current = state.preferences.firstOrNull { it.id == target.preferenceId }
        val before = target.before
        val preferences = when {
            before != null && current != null -> state.preferences.map {
                if (it.id == target.preferenceId) before else it
            }
            before != null -> state.preferences + before
            else -> state.preferences.filter { it.id != target.preferenceId }
        }
        val marked = state.copy(
            log = state.log.map {
                if (it.id == target.id) it.copy(undone = Undone(change.at, actor, change.opId)) else it
            },
        )
        return Writer(marked, change.opId, change.at, actor).write("undone", target.preferenceId, current, preferences)
    }

    /** Homemind's `parsePreferenceInput`: the fields of a preference, cleaned and checked. */
    @Suppress("ReturnCount")
    private fun input(op: JsonObject, context: NamespaceContext): Preference? {
        val text = clean(op["text"], MAX_TEXT) ?: return null
        if (text.isEmpty()) return null
        val reason = clean(op["reason"], MAX_TEXT) ?: return null
        val strength = string(op["strength"])?.takeIf { it == HARD || it == SOFT } ?: return null
        val about = op["aboutMemberId"]
        var aboutMemberId: String? = null
        if (!absent(about)) {
            aboutMemberId = string(about)?.takeIf { id -> context.family.members.any { it.id == id } } ?: return null
        }
        val scope = clean(op["scope"], MAX_SCOPE) ?: return null
        if (scope.isNotEmpty() && !SCOPE.matches(scope)) return null
        return Preference(
            id = "",
            aboutMemberId = aboutMemberId,
            text = text,
            reason = reason.ifEmpty { null },
            strength = strength,
            scope = scope.ifEmpty { null }?.lowercase(),
        )
    }

    private class Writer(val state: PreferenceState, val opId: String, val at: String, val actor: String) {
        fun write(
            action: String,
            preferenceId: String,
            before: Preference?,
            preferences: List<Preference>,
        ): NamespaceStep {
            val record = PreferenceChange(opId, at, actor, action, preferenceId, before)
            return NamespaceStep.Applied(encode(state.copy(preferences = preferences, log = state.log + record)))
        }
    }

    companion object {
        const val NAME = "preferences"
        val KINDS = setOf("save", "remove", "undo")
        const val HARD = "hard"
        const val SOFT = "soft"
        const val MAX_TEXT = 500
        const val MAX_SCOPE = 60
        const val MAX_COUNT = 200
        private val ID = Regex("^[a-z0-9][a-z0-9_-]{0,63}$")
        private val SCOPE = Regex("^[\\p{L}\\p{N}][\\p{L}\\p{N} _-]*$")
        private val CONTROL = Regex("[\\u0000-\\u001f\\u007f]")
        private const val JS_SPACE_SET = "\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A" +
            "\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF"
        private const val JS_SPACE = "[$JS_SPACE_SET]"
        private val SPACES = Regex("$JS_SPACE+")
        private val NORM =
            Regex(
                "[$JS_SPACE_SET\\p{P}\\p{S}]+",
            )

        internal val json = Json {
            ignoreUnknownKeys = false
            encodeDefaults = false
            explicitNulls = false
        }

        fun encode(state: PreferenceState): JsonObject = json.encodeToJsonElement(
            PreferenceState.serializer(),
            state,
        ) as JsonObject

        /** The preferences stored in a scope's partition (none before the first op). */
        fun of(own: JsonObject?): List<Preference> =
            own?.let { json.decodeFromJsonElement(PreferenceState.serializer(), it).preferences }.orEmpty()

        private fun reject(code: RejectCode): NamespaceStep = NamespaceStep.Rejected(code)

        private fun string(x: JsonElement?): String? = (x as? JsonPrimitive)?.takeIf { it.isString }?.content

        private fun absent(x: JsonElement?): Boolean = x == null || x is JsonNull || string(x) == ""

        /** Homemind's `cleanText`: "" when absent, null when not acceptable. */
        @Suppress("ReturnCount")
        private fun clean(x: JsonElement?, max: Int): String? {
            if (absent(x)) return ""
            val raw = string(x) ?: return null
            val text = raw.trim { JS_SPACE_CHARS.contains(it) }.replace(SPACES, " ")
            if (text.length > max) return null
            if (CONTROL.containsMatchIn(text)) return null
            return text
        }

        private val JS_SPACE_CHARS: Set<Char> = buildSet {
            addAll(listOf('\t', '\n', '\u000B', '\u000C', '\r', ' ', ' ', ' '))
            for (c in ' '..' ') add(c)
            addAll(listOf(0x2028, 0x2029, 0x202F, 0x205F, 0x3000, 0xFEFF).map { it.toChar() })
        }

        fun norm(s: String?): String = (s ?: "").lowercase().replace(NORM, "")

        private fun same(a: Preference, b: Preference) =
            a.text == b.text && a.reason == b.reason && a.aboutMemberId == b.aboutMemberId &&
                a.strength == b.strength && a.scope == b.scope
    }
}
