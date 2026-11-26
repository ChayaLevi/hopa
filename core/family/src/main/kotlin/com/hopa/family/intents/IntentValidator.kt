package com.hopa.family.intents

import com.hopa.family.intents.Checker.Companion.absent
import com.hopa.family.intents.Checker.Companion.integer
import com.hopa.family.intents.Checker.Companion.obj
import com.hopa.family.intents.Checker.Companion.string
import com.hopa.family.time.FamilyClock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Homemind's `validateIntent`: a model reply becomes a [ChatIntent] built from known fields only,
 * or the list of what was wrong. Ids must be ids the member may see ([IntentContext] is built from
 * their view), so a reply can never point a teen at a parents item, whatever the message said.
 * The fields are read in Homemind's order, so the problems come out in the same order.
 */
object IntentValidator {
    private val LOCAL_DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val HHMM = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
    private val SCOPE = Regex("^[\\p{L}\\p{N}][\\p{L}\\p{N} _-]*$")
    private const val MAX_WEEKDAY = 6L
    private const val MAX_LEAD = 24 * 60L
    private const val DEFAULT_LEAD = 30
    private const val MAX_QTY = 99L
    private const val MAX_SHOPPING = 10
    private const val MAX_NOTE = 120
    private const val MAX_NAME = 80
    private const val MAX_SCOPE = 60

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun validate(raw: JsonElement?, ctx: IntentContext): Validation<ChatIntent> {
        val o = obj(raw) ?: return Validation.Invalid(listOf("the reply is not a JSON object"))
        val c = Checker(ctx.nowMs)
        val name = c.oneOf(o["intent"], "intent", ChatIntent.NAMES)
        if (c.problems.isNotEmpty()) return Validation.Invalid(c.problems)
        val members = ctx.memberIds
        val items = ctx.itemIds
        val value: ChatIntent = when (name) {
            "ask" -> ChatIntent.Ask(
                answer = IntentPrompts.scrubIds(c.text(o["answer"], "answer", IntentLimits.ANSWER), ctx.titles),
                itemIds = c.ids(o["itemIds"], "itemIds", items, IntentLimits.ITEM_IDS),
            )
            "update_item" -> {
                val title = c.optText(o["title"], "title", IntentLimits.TITLE)
                val start = c.optTime(o["start"], "start")
                if (title.isNullOrEmpty() && start.isNullOrEmpty() && c.problems.isEmpty()) {
                    c.bad("title", "and start are both empty: nothing to change")
                }
                ChatIntent.UpdateItem(
                    c.id(o["itemId"], "itemId", items),
                    title?.ifEmpty { null },
                    start?.ifEmpty { null },
                )
            }
            "split_item" -> {
                val itemId = c.id(o["itemId"], "itemId", items)
                if (itemId.isNotEmpty() && itemId in ctx.eventIds) {
                    c.bad(
                        "itemId",
                        "is an event; only a task or a preparation can be split",
                    )
                }
                val rawParts = o["parts"]
                val list = rawParts as? JsonArray ?: JsonArray(emptyList()).also { c.bad("parts", "is not a list") }
                if (rawParts is JsonArray && list.size !in 2..IntentLimits.SPLIT_PARTS) {
                    c.bad("parts", "must have 2 to ${IntentLimits.SPLIT_PARTS} entries")
                }
                val parts = list.take(IntentLimits.SPLIT_PARTS).mapIndexed { i, p ->
                    val part = obj(p) ?: return@mapIndexed ChatIntent.SplitItem.Part("").also {
                        c.bad("parts[$i]", "is not an object")
                    }
                    val due = c.optTime(part["due"], "parts[$i].due")
                    ChatIntent.SplitItem.Part(
                        c.text(part["title"], "parts[$i].title", IntentLimits.TITLE),
                        due?.ifEmpty { null },
                    )
                }
                ChatIntent.SplitItem(itemId, parts)
            }
            "create_event" -> createEvent(o, c, ctx)
            "create_task" -> {
                val due = c.optTime(o["due"], "due")
                val ownerId = c.optId(o["ownerId"], "ownerId", members)
                ChatIntent.CreateTask(
                    title = c.text(o["title"], "title", IntentLimits.TITLE),
                    due = due?.ifEmpty { null },
                    ownerId = ownerId?.ifEmpty { null },
                    forMemberIds = c.ids(o["forMemberIds"], "forMemberIds", members, members.size),
                )
            }
            "add_preparation" -> {
                val due = c.optTime(o["due"], "due")
                ChatIntent.AddPreparation(
                    eventId = c.id(o["eventId"], "eventId", ctx.eventIds),
                    title = c.text(o["title"], "title", IntentLimits.TITLE),
                    due = due?.ifEmpty { null },
                )
            }
            "assign" -> ChatIntent.Assign(c.id(o["itemId"], "itemId", items), c.id(o["memberId"], "memberId", members))
            "claim" -> ChatIntent.Claim(c.id(o["itemId"], "itemId", items))
            "mark_done" -> ChatIntent.MarkDone(c.id(o["itemId"], "itemId", items))
            "delete_item" -> ChatIntent.DeleteItem(c.id(o["itemId"], "itemId", items))
            "why" -> ChatIntent.Why(c.id(o["itemId"], "itemId", items))
            "add_preference" -> {
                val reason = c.optText(o["reason"], "reason", IntentLimits.TEXT)
                val about = c.optId(o["aboutMemberId"], "aboutMemberId", members)
                val scope = if (absent(o["scope"]) || string(o["scope"]) == "") {
                    null
                } else {
                    c.text(
                        o["scope"],
                        "scope",
                        MAX_SCOPE,
                    )
                }
                if (!scope.isNullOrEmpty() && !SCOPE.matches(scope)) c.bad("scope", "is not a short name")
                ChatIntent.AddPreference(
                    text = c.text(o["text"], "text", IntentLimits.TEXT),
                    reason = reason?.ifEmpty { null },
                    aboutMemberId = about?.ifEmpty { null },
                    strength = c.oneOf(o["strength"], "strength", listOf("hard", "soft")),
                    scope = scope?.ifEmpty { null },
                )
            }
            "add_to_shopping" -> addToShopping(o, c, ctx)
            "create_rule" -> {
                val weekday = integer(o["weekday"])
                if (weekday == null || weekday !in 0..MAX_WEEKDAY) c.bad("weekday", "is not 0 to 6")
                val time = string(o["time"])?.takeIf { HHMM.matches(it) } ?: "".also { c.bad("time", "is not HH:MM") }
                val lead = if (absent(o["leadMinutes"])) DEFAULT_LEAD.toLong() else integer(o["leadMinutes"])
                if (lead == null || lead !in 0..MAX_LEAD) c.bad("leadMinutes", "is not 0 to 1440")
                ChatIntent.CreateRule(
                    title = c.text(o["title"], "title", IntentLimits.TITLE),
                    weekday = (weekday ?: 0).toInt(),
                    time = time,
                    leadMinutes = (lead ?: 0).toInt(),
                    action = c.text(o["action"], "action", IntentLimits.TEXT),
                )
            }
            "report_situation" -> {
                val memberId = c.optId(o["memberId"], "memberId", members)
                val date = string(o["date"])?.takeIf { LOCAL_DATE.matches(it) }
                    ?: "".also { c.bad("date", "is not YYYY-MM-DD") }
                if (date.isNotEmpty()) {
                    val today = FamilyClock.localParts(ctx.nowMs, ctx.timezone).date
                    val ms = noonOf(date)?.let { d -> noonOf(today)?.let { d - it } }
                    if (ms == null || ms < -IntentLimits.PAST_MS || ms > IntentLimits.SITUATION_AHEAD_MS) {
                        c.bad(
                            "date",
                            "is too far from today",
                        )
                    }
                }
                ChatIntent.ReportSituation(
                    situation = c.oneOf(o["situation"], "situation", ChatIntent.SITUATIONS),
                    memberId = memberId?.ifEmpty { null },
                    date = date,
                    summary = c.text(o["summary"], "summary", IntentLimits.TEXT),
                )
            }
            else -> error("unreachable: $name")
        }
        return if (c.problems.isEmpty()) Validation.Ok(value) else Validation.Invalid(c.problems)
    }

    private fun createEvent(o: JsonObject, c: Checker, ctx: IntentContext): ChatIntent {
        val members = ctx.memberIds
        val start = c.time(o["start"], "start")
        val end = c.optTime(o["end"], "end")
        if (start.isNotEmpty() && !end.isNullOrEmpty()) {
            val span = FamilyClock.millis(end) - FamilyClock.millis(start)
            if (span <= 0 || span > IntentLimits.SPAN_MS) c.bad("end", "is not after start, within 14 days")
        }
        val visibility = if (absent(
                o["visibility"],
            )
        ) {
            "family"
        } else {
            c.oneOf(o["visibility"], "visibility", listOf("family", "parents"))
        }
        if (visibility == "parents" && !ctx.isParent) c.bad("visibility", "can be \"parents\" only for a parent")
        return ChatIntent.CreateEvent(
            title = c.text(o["title"], "title", IntentLimits.TITLE),
            start = start,
            end = end?.ifEmpty { null },
            allDay = c.bool(o["allDay"], "allDay", false),
            forMemberIds = c.ids(o["forMemberIds"], "forMemberIds", members, members.size),
            unavailableMemberIds = c.ids(o["unavailableMemberIds"], "unavailableMemberIds", members, members.size),
            visibility = visibility,
            importance = if (absent(
                    o["importance"],
                )
            ) {
                "normal"
            } else {
                c.oneOf(o["importance"], "importance", listOf("normal", "critical"))
            },
        )
    }

    private fun addToShopping(o: JsonObject, c: Checker, ctx: IntentContext): ChatIntent {
        val rawItems = o["items"]
        val list = rawItems as? JsonArray ?: JsonArray(emptyList()).also { c.bad("items", "is not a list") }
        if (rawItems is JsonArray && list.size !in 1..MAX_SHOPPING) c.bad("items", "must have 1 to 10 entries")
        val entries = list.take(MAX_SHOPPING).mapIndexed { i, entry ->
            val p = "items[$i]"
            val e = obj(entry) ?: return@mapIndexed ChatIntent.AddToShopping.Entry("", "").also {
                c.bad(p, "is not an object")
            }
            val qty = if (absent(e["qty"])) null else integer(e["qty"])
            if (!absent(e["qty"]) && (qty == null || qty !in 1..MAX_QTY)) {
                c.bad(
                    "$p.qty",
                    "is not a whole number from 1 to 99",
                )
            }
            val note = c.optText(e["note"], "$p.note", MAX_NOTE)
            ChatIntent.AddToShopping.Entry(
                storeId = c.id(e["storeId"], "$p.storeId", ctx.storeIds),
                name = c.text(e["name"], "$p.name", MAX_NAME),
                qty = qty?.toInt(),
                note = note?.ifEmpty { null },
            )
        }
        return ChatIntent.AddToShopping(entries)
    }

    /** Noon UTC of a local date, as Homemind measures the distance between two dates. */
    private fun noonOf(date: String): Long? = JsDate.parse("${date}T12:00:00Z")
}
