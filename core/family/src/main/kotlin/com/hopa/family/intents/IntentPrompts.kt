package com.hopa.family.intents

import com.hopa.family.model.FamilyRole
import com.hopa.family.model.Importance
import com.hopa.family.model.Item
import com.hopa.family.model.ItemStatus
import com.hopa.family.model.ItemType
import com.hopa.family.model.Ownership
import com.hopa.family.model.Scope
import com.hopa.family.scope.MemberView
import com.hopa.family.time.FamilyClock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * What Homemind asks the model (`intents.ts`), built from one member's view only, so a prompt holds
 * nothing that member may not see. Message, board and chat text go in as DATA, in blocks the text
 * cannot close. Dates are given with today's local date, time and UTC offset. Byte for byte the
 * prompts every client sends (`logic-intents.json`).
 */
object IntentPrompts {
    private val WEEKDAYS = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    private const val MINUTE_MS = 60_000L
    private const val MINUTES_PER_HOUR = 60L
    private const val MAX_WAITING = 15
    private const val MAX_WAITING_NAME = 60
    private const val MAX_NOTES = 200
    private const val INTENT_TOKENS = 800
    private const val EXTRACT_TOKENS = 2000

    /** The items that go into the prompt: on the board, near now (or undated, or weekly), soonest first. */
    fun contextItems(view: MemberView, nowMs: Long): List<Pair<Scope, Item>> {
        val from = nowMs - IntentLimits.PAST_MS
        val to = nowMs + IntentLimits.CONTEXT_AHEAD_MS
        return view.items
            .filter { it.item.status == ItemStatus.OPEN || it.item.status == ItemStatus.PROPOSED }
            .filter { s ->
                val start = s.item.start
                start == null || s.item.recurrence != null || FamilyClock.millis(start) in from..to
            }
            .sortedBy { it.item.start ?: "9" }
            .take(IntentLimits.CONTEXT_ITEMS)
            .map { it.scope to it.item }
    }

    fun intentContextFor(view: MemberView, nowMs: Long, stores: List<IntentStore> = emptyList()): IntentContext {
        val items = contextItems(view, nowMs).map { it.second }
        val me = view.family.members.firstOrNull { it.id == view.memberId }
        return IntentContext(
            memberId = view.memberId,
            isParent = me?.role == FamilyRole.PARENT,
            nowMs = nowMs,
            timezone = view.family.timezone,
            memberIds = view.family.members.map { it.id },
            itemIds = items.map { it.id },
            eventIds = items.filter { it.type == ItemType.EVENT }.map { it.id },
            titles = items.associate { it.id to it.title },
            storeIds = stores.map { it.id },
        )
    }

    /** The board as the model sees it: compact JSON, only from the member's view. Sensitive notes are left out. */
    fun boardContext(
        view: MemberView,
        nowMs: Long,
        stores: List<IntentStore>,
        preferences: List<PromptPreference>,
    ): String {
        val zone = view.family.timezone
        val me = view.family.members.firstOrNull { it.id == view.memberId }
        return buildJsonObject {
            put(
                "me",
                if (me == null) {
                    buildJsonObject { put("id", view.memberId) }
                } else {
                    buildJsonObject {
                        put("id", me.id)
                        put("name", me.name)
                        put("role", role(me.role))
                    }
                },
            )
            put(
                "members",
                JsonArray(
                    view.family.members.map { m ->
                        buildJsonObject {
                            put("id", m.id)
                            put("name", m.name)
                            m.nameZh?.let { put("nameZh", it) }
                            put("role", role(m.role))
                        }
                    },
                ),
            )
            put("items", JsonArray(contextItems(view, nowMs).map { (scope, item) -> itemJson(scope, item, zone) }))
            put(
                "preferences",
                JsonArray(
                    preferences.map { p ->
                        buildJsonObject {
                            p.about?.let { put("about", it) }
                            put("text", p.text)
                            p.reason?.let { put("reason", it) }
                            put("strength", p.strength)
                            p.scope?.let { put("scope", it) }
                        }
                    },
                ),
            )
            if (stores.isNotEmpty()) {
                put(
                    "stores",
                    JsonArray(
                        stores.map { s ->
                            buildJsonObject {
                                put("id", s.id)
                                put("name", s.name)
                                s.nameZh?.let { put("nameZh", it) }
                                put(
                                    "waiting",
                                    JsonArray(
                                        s.waiting.take(MAX_WAITING).map { JsonPrimitive(it.take(MAX_WAITING_NAME)) },
                                    ),
                                )
                            }
                        },
                    ),
                )
            }
        }.toString()
    }

    @Suppress("CyclomaticComplexMethod")
    private fun itemJson(scope: Scope, i: Item, zone: String): JsonObject = buildJsonObject {
        put("id", i.id)
        put("type", i.type.name.lowercase())
        put("title", i.title)
        i.titleZh?.let { put("titleZh", it) }
        // Local time with its offset: with UTC times the model misread dates.
        i.start?.let { put("start", localIso(it, zone)) }
        i.end?.let { put("end", localIso(it, zone)) }
        if (i.allDay == true) put("allDay", true)
        i.recurrence?.let { r -> put("weekly", JsonArray(r.byWeekday.map { JsonPrimitive(it) })) }
        i.parentEventId?.let { put("event", it) }
        if (i.status == ItemStatus.PROPOSED) put("proposed", true)
        if (scope == Scope.PARENTS) put("parentsOnly", true)
        if (i.importance == Importance.CRITICAL) put("critical", true)
        put("for", JsonArray(i.forMemberIds.map { JsonPrimitive(it) }))
        if (i.unavailableMemberIds.isNotEmpty()) {
            put(
                "away",
                JsonArray(i.unavailableMemberIds.map { JsonPrimitive(it) }),
            )
        }
        when (val own = i.ownership) {
            is Ownership.Assigned -> {
                put("owner", own.ownerId)
                put("confirmed", own.confirmed)
            }
            is Ownership.Open -> {
                put("owner", JsonNull)
                put("whoIsTakingThis", true)
            }
            Ownership.None -> put("owner", JsonNull)
        }
        val notes = i.notes
        if (!notes.isNullOrEmpty() && !i.sensitive) put("notes", notes.take(MAX_NOTES))
    }

    private fun role(role: FamilyRole) = role.name.lowercase()

    /** Data cannot close its own block: the markers are neutralised inside it. */
    fun escapeData(text: String): String = text.replace("<<<", "‹‹‹").replace(">>>", "›››")

    fun dataBlock(name: String, text: String): String = "<<<BEGIN $name>>>\n${escapeData(text)}\n<<<END $name>>>"

    /** "Today is Thursday 2026-11-05, 14:20 local time, time zone Asia/Shanghai (UTC+08:00)." and the offset. */
    fun todayLine(nowMs: Long, zone: String): Pair<String, String> {
        val p = FamilyClock.localParts(nowMs, zone)
        val localMs = LocalDateTime.of(
            LocalDate.parse(p.date),
            LocalTime.parse(p.time),
        ).toInstant(ZoneOffset.UTC).toEpochMilli()
        val minutes = ((localMs - Math.floorDiv(nowMs, MINUTE_MS) * MINUTE_MS).toDouble() / MINUTE_MS).roundToLong()
        val sign = if (minutes < 0) "-" else "+"
        val a = abs(minutes)
        val offset = "$sign${(a / MINUTES_PER_HOUR).toString().padStart(
            2,
            '0',
        )}:${(a % MINUTES_PER_HOUR).toString().padStart(2, '0')}"
        val text = "Today is ${WEEKDAYS[p.weekday]} ${p.date}, ${p.time} local time, time zone $zone (UTC$offset)."
        return text to offset
    }

    /** `2026-11-05T00:00+08:00`: the family's local time, with the offset in force then. */
    fun localIso(iso: String, zone: String): String {
        val ms = JsDate.parse(iso) ?: runCatching { FamilyClock.millis(iso) }.getOrNull() ?: return iso
        val p = FamilyClock.localParts(ms, zone)
        return "${p.date}T${p.time}${todayLine(ms, zone).second}"
    }

    /** The last lines, one per line, each cut short. */
    fun recentChatText(lines: List<ChatLine>): String =
        lines.takeLast(IntentLimits.RECENT_LINES).joinToString("\n") { l ->
            val who = if (l.from == "member") "Member" else "Homemind"
            "$who: ${JsText.trim(JsText.collapse(l.text)).take(IntentLimits.RECENT_LINE)}"
        }

    /**
     * An answer names items by title, never by id: the model was told so, and this makes sure.
     * "(item-x)" goes, and a bare id becomes the item's title in quotes.
     */
    fun scrubIds(answer: String, titles: Map<String, String>): String {
        var out = answer
        for (id in titles.keys.sortedByDescending { it.length }) {
            if (id !in out) continue
            val esc = Regex.escape(id)
            val s = JsText.SPACE
            out = out.replace(Regex("$s*[(（]$s*(?:id[:：]?$s*)?$esc$s*[)）]"), "")
            out = out.replace(Regex("(?<![\\w-])$esc(?![\\w-])"), Regex.escapeReplacement("“${titles.getValue(id)}”"))
        }
        return out
    }

    @Suppress("LongParameterList")
    fun intentPrompt(
        view: MemberView,
        nowMs: Long,
        lang: Lang,
        text: String,
        recent: List<ChatLine> = emptyList(),
        stores: List<IntentStore> = emptyList(),
        preferences: List<PromptPreference> = emptyList(),
    ): ModelRequest {
        val (line, offset) = todayLine(nowMs, view.family.timezone)
        val me = view.family.members.firstOrNull { it.id == view.memberId }
        val writer = if (me != null) "${me.name} (id ${me.id}, ${role(me.role)})" else view.memberId
        val system = PromptText.intentSystem(line, offset, writer, lang.wire)
        val recentText = recentChatText(recent)
        val user = dataBlock("BOARD", boardContext(view, nowMs, stores, preferences)) + "\n\n" +
            (if (recentText.isNotEmpty()) dataBlock("RECENT CHAT", recentText) + "\n\n" else "") +
            dataBlock("MESSAGE", text.take(IntentLimits.MESSAGE)) +
            "\n\nReply with the JSON object for the MESSAGE."
        return ModelRequest(purpose = "intent", system = system, user = user, maxTokens = INTENT_TOKENS)
    }

    /**
     * The extraction prompt for a text source (a message, an email, pasted text). Screenshots are
     * read on the device by a model that takes images; that request shape belongs to the app.
     */
    fun extractionPrompt(nowMs: Long, zone: String, text: String?): ModelRequest {
        val (line, offset) = todayLine(nowMs, zone)
        val parts = buildList {
            if (!text.isNullOrEmpty()) add(dataBlock("SOURCE TEXT", text.take(IntentLimits.SOURCE_TEXT)))
            add("Reply with the JSON object.")
        }
        return ModelRequest(
            purpose = "extract",
            system = PromptText.extractionSystem(line, offset),
            user = parts.joinToString("\n\n"),
            maxTokens = EXTRACT_TOKENS,
        )
    }
}
