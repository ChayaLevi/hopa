@file:Suppress("ktlint:standard:max-line-length")

package com.hopa.family.preferences

import com.hopa.family.model.BoardOp
import com.hopa.family.model.Item
import com.hopa.family.model.ItemStatus
import com.hopa.family.model.ItemType
import com.hopa.family.model.Member

/**
 * Where a preference applies, and what a hard one rules out (Homemind's `preferences.ts`). A
 * preference about a member applies to things for that member; a scoped one (`kindergarten-party`)
 * only where its words match (both languages). A hard preference (allergy, dietary law) is never
 * overridden: a preparation or task whose title names something it rules out is blocked. The word
 * rules are Homemind's JavaScript ones, kept exact; `logic-preferences.json` is the contract. Pure.
 */
object PreferenceMatching {
    /** Words for the scope names people and the model use. A scope needs every concept it names. */
    private val SCOPE_CONCEPTS: List<Pair<String, List<String>>> = listOf(
        "kindergarten" to listOf("kindergarten", "preschool", "pre-school", "nursery", "daycare", "幼儿园", "幼稚园", "托儿所"),
        "school" to listOf("school", "学校"),
        "party" to listOf("party", "parties", "celebration", "picnic", "派对", "聚会", "联欢", "庆祝", "野餐"),
        "birthday" to listOf("birthday", "生日"),
        "dinner" to listOf("dinner", "supper", "晚饭", "晚餐", "聚餐"),
        "lunch" to listOf("lunch", "lunchbox", "午饭", "午餐", "便当"),
        "breakfast" to listOf("breakfast", "早饭", "早餐"),
        "snack" to listOf("snack", "snacks", "零食", "点心"),
        "trip" to listOf("trip", "outing", "excursion", "郊游", "出游", "旅行", "春游", "秋游"),
        "sport" to listOf(
            "sport", "sports", "swim", "swimming", "football", "soccer", "basketball", "运动", "体育", "游泳", "足球", "篮球",
        ),
        "music" to listOf("music", "piano", "violin", "音乐", "钢琴", "小提琴"),
        "weekend" to listOf("weekend", "周末"),
    )
    private val SCOPE_STOP =
        setOf("and", "or", "the", "for", "with", "all", "any", "events", "event", "time", "times", "day", "days")

    /** Things hard preferences rule out, with their words in both languages. */
    private val AVOID_CONCEPTS: List<Pair<String, List<String>>> = listOf(
        "peanut" to listOf("peanut", "peanuts", "groundnut", "花生"),
        "nut" to listOf(
            "nut", "nuts", "tree nut", "walnut", "almond", "cashew", "hazelnut", "pistachio", "pecan",
            "坚果", "核桃", "杏仁", "腰果", "榛子", "开心果",
        ),
        "kiwi" to listOf("kiwi", "kiwis", "kiwifruit", "猕猴桃", "奇异果"),
        "egg" to listOf("egg", "eggs", "鸡蛋"),
        "dairy" to listOf("dairy", "milk", "cheese", "butter", "cream", "yogurt", "yoghurt", "牛奶", "奶酪", "乳制品", "黄油", "奶油", "酸奶"),
        "pork" to listOf("pork", "ham", "bacon", "sausage", "猪肉", "火腿", "培根", "香肠"),
        "gluten" to listOf("gluten", "wheat", "小麦", "面筋", "麸质"),
        "shellfish" to listOf("shellfish", "shrimp", "prawn", "crab", "lobster", "虾", "蟹", "龙虾", "贝类"),
        "fish" to listOf("fish", "鱼"),
        "sesame" to listOf("sesame", "芝麻"),
        "soy" to listOf("soy", "soya", "tofu", "大豆", "豆腐"),
        "meat" to listOf("meat", "beef", "chicken", "lamb", "肉", "牛肉", "鸡肉", "羊肉"),
    )

    // JavaScript's \b is ASCII-only; these are its boundaries.
    private const val B = "(?<![A-Za-z0-9_])"
    private const val E = "(?![A-Za-z0-9_])"
    private const val BOTH = "(?:(?<=[A-Za-z0-9_])(?![A-Za-z0-9_])|(?<![A-Za-z0-9_])(?=[A-Za-z0-9_]))"
    private const val S = "[\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]"
    private val AVOID_EN = listOf(
        Regex(
            "$BOTH(?:no|not|never|avoid|avoids|without|free of|don'?t eat|doesn'?t eat|can'?t eat|cannot eat|can'?t have|" +
                "cannot have|allergic to|allergy to|intolerant to)$S+(?:any$S+)?(.+)\\z",
            RegexOption.IGNORE_CASE,
        ),
        Regex("$B([a-z]+)[- ](?:free|allergy|allergies|intolerance)$E", RegexOption.IGNORE_CASE),
    )
    private val AVOID_ZH = listOf(
        Regex("(?:不吃|不能吃|不可以吃|不要|不能有|禁止|忌口|忌|避免|不含|别带)(.+)\\z"),
        Regex("对(.+?)过敏"),
        Regex("^(.+?)过敏"),
    )
    private val SPLIT_EN = Regex(",|/|${BOTH}and$BOTH|${BOTH}or$BOTH|、|和|或", RegexOption.IGNORE_CASE)
    private val SPLIT_ZH = Regex("[,，、和或/]")
    private val TRAILING = Regex("[.!。！]+\\z")
    private val FILLER = Regex("$BOTH(at all|please|food|foods|products?)$BOTH")
    private val ZH_SUFFIX = Regex("(食品|食物|制品|类)\\z")
    private val ASCII = Regex("^[a-z0-9-]+$")
    private val SCOPE_SPLIT =
        Regex("[\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF_-]+")

    private fun ascii(w: String) = ASCII.matches(w)

    /** An English word matches at a word start (`peanut` matches `peanuts`); Chinese words anywhere. */
    private fun hasWord(haystack: String, word: String): Boolean = if (ascii(
            word,
        )
    ) {
        Regex("(^|[^a-z0-9])${Regex.escape(word)}").containsMatchIn(haystack)
    } else {
        haystack.contains(word)
    }

    /** What a scope needs: concepts (any of their words) and leftover literal words. */
    fun scopeTerms(scope: String): Pair<List<String>, List<String>> {
        var rest = scope.lowercase()
        val concepts = mutableListOf<String>()
        for ((concept, words) in SCOPE_CONCEPTS) {
            val all = listOf(concept) + words
            val hit = all.sortedByDescending { it.length }.firstOrNull { hasWord(rest, it) }
            if (hit != null) {
                concepts += concept
                for (w in all) rest = rest.split(w).joinToString(" ")
            }
        }
        val literals = rest.split(SCOPE_SPLIT).map { it.trim() }
            .filter { w -> w.isNotEmpty() && w !in SCOPE_STOP && if (ascii(w)) w.length >= 3 else w.length >= 2 }
        return concepts to literals
    }

    fun scopeMatches(scope: String, context: PreferenceContext): Boolean {
        val haystack = (context.texts + context.tags).joinToString(" \n ").lowercase()
        val (concepts, literals) = scopeTerms(scope)
        if (concepts.isEmpty() && literals.isEmpty()) return false
        val words = SCOPE_CONCEPTS.toMap()
        return concepts.all { c -> (listOf(c) + words.getValue(c)).any { hasWord(haystack, it) } } &&
            literals.all { hasWord(haystack, it) }
    }

    /**
     * Whether a preference applies. About someone: things for that member; a scoped one also things for
     * nobody in particular (a party read from a group message) when the scope matches.
     */
    fun appliesTo(preference: Preference, context: PreferenceContext): Boolean {
        val about = preference.aboutMemberId
        val memberOk = about == null || about in context.memberIds || (context.memberIds.isEmpty() && preference.scope != null)
        return memberOk && (preference.scope == null || scopeMatches(preference.scope, context))
    }

    /** Applicable preferences, hard first, then scoped (more specific) before general. */
    fun applicable(preferences: List<Preference>, context: PreferenceContext): List<Preference> {
        fun rank(p: Preference) =
            (if (p.strength == PreferencesNamespace.HARD) 0 else 2) + (if (p.scope != null) 0 else 1)
        return preferences.filter { appliesTo(it, context) }.sortedBy(::rank)
    }

    /**
     * An item's context: its own words and its event's, and who it is for. A preparation for nobody
     * in particular is for whoever its event is for, or else the whole family.
     */
    fun itemContext(items: List<Item>, members: List<Member>, item: Item): PreferenceContext {
        val parent = item.parentEventId?.let { id -> items.firstOrNull { it.id == id } }
        var memberIds = item.forMemberIds
        if (memberIds.isEmpty() && item.type == ItemType.PREPARATION) {
            memberIds = parent?.forMemberIds?.takeIf { it.isNotEmpty() } ?: members.map { it.id }
        }
        val texts = listOfNotNull(
            item.title,
            item.titleZh,
            item.notes,
            parent?.title,
            parent?.titleZh,
            parent?.notes,
            item.source.label,
        )
            .filter { it.isNotEmpty() }
        return PreferenceContext(memberIds, texts)
    }

    /** The things a hard preference rules out: concept names or literal words. */
    @Suppress("CyclomaticComplexMethod")
    fun avoidedTerms(preference: Preference): List<String> {
        if (preference.strength != PreferencesNamespace.HARD) return emptyList()
        val found = linkedSetOf<String>()
        for (text in listOfNotNull(preference.text, preference.textZh).filter { it.isNotEmpty() }) {
            val objects = mutableListOf<String>()
            for (re in AVOID_EN) re.find(text)?.let { objects += it.groupValues[1].split(SPLIT_EN) }
            for (re in AVOID_ZH) re.find(text)?.let { objects += it.groupValues[1].split(SPLIT_ZH) }
            for (raw in objects) {
                val term = raw.lowercase().replace(
                    TRAILING,
                    "",
                ).replace(FILLER, "").replace(ZH_SUFFIX, "").trim { it.isJsSpace() }
                if (term.isEmpty()) continue
                val concept = AVOID_CONCEPTS.firstOrNull { (_, words) -> term in words }
                if (concept != null) {
                    found += concept.first
                } else if (if (ascii(term.replace(" ", ""))) term.length >= 3 else term.isNotEmpty()) {
                    found += term
                }
            }
        }
        return found.toList()
    }

    private fun Char.isJsSpace() = this in " \t\n\u000B\u000C\r      　" || this in ' '..' '

    private fun termWords(term: String): List<String> = AVOID_CONCEPTS.toMap()[term] ?: listOf(term)

    /** Hard preferences that rule out a choice (its words in either language), where they apply. */
    fun checkChoice(
        preferences: List<Preference>,
        context: PreferenceContext,
        choice: List<String>,
    ): List<BlockedChoice> {
        val haystack = choice.filter { it.isNotEmpty() }.joinToString(" \n ").lowercase()
        val blocked = mutableListOf<BlockedChoice>()
        for (preference in preferences) {
            if (preference.strength != PreferencesNamespace.HARD || !appliesTo(preference, context)) continue
            for (term in avoidedTerms(preference)) {
                val word = termWords(term).firstOrNull { hasWord(haystack, it) } ?: continue
                blocked += BlockedChoice(preference, word)
                break
            }
        }
        return blocked
    }

    /**
     * Whether a board op would add something a hard preference rules out: a preparation or task
     * created, retitled, or approved (with an event's proposed preparations). Events are not choices.
     * [items] are the board items of the op's scope.
     */
    fun blockedOp(
        preferences: List<Preference>,
        items: List<Item>,
        members: List<Member>,
        op: BoardOp,
    ): List<BlockedChoice> {
        fun check(item: Item): List<BlockedChoice> = if (item.type == ItemType.EVENT) {
            emptyList()
        } else {
            checkChoice(preferences, itemContext(items, members, item), listOf(item.title, item.titleZh ?: ""))
        }
        return when (op) {
            is BoardOp.Create -> {
                val i = op.item
                check(draft(i.id, i.type, i.title, i.notes, i.forMemberIds.orEmpty(), i.parentEventId))
            }
            is BoardOp.Edit -> {
                val current = items.firstOrNull { it.id == op.itemId } ?: return emptyList()
                if (op.patch.title == null && op.patch.forMemberIds == null) return emptyList()
                check(
                    current.copy(
                        title = op.patch.title ?: current.title,
                        titleZh = if (op.patch.title != null) null else current.titleZh,
                        forMemberIds = op.patch.forMemberIds ?: current.forMemberIds,
                    ),
                )
            }
            is BoardOp.Approve -> {
                val current = items.firstOrNull { it.id == op.itemId } ?: return emptyList()
                val preps = if (current.type == ItemType.EVENT) {
                    items.filter { it.parentEventId == current.id && it.status == ItemStatus.PROPOSED }
                } else {
                    emptyList()
                }
                (listOf(current) + preps).flatMap(::check)
            }
            else -> emptyList()
        }
    }

    @Suppress("LongParameterList")
    private fun draft(
        id: String,
        type: ItemType,
        title: String,
        notes: String?,
        forMemberIds: List<String>,
        parentEventId: String?,
    ) = Item(
        id = id,
        type = type,
        title = title,
        notes = notes,
        forMemberIds = forMemberIds,
        ownership = com.hopa.family.model.Ownership.None,
        sensitive = false,
        importance = com.hopa.family.model.Importance.NORMAL,
        unavailableMemberIds = emptyList(),
        parentEventId = parentEventId,
        status = ItemStatus.OPEN,
        source = com.hopa.family.model.Source(com.hopa.family.model.SourceKind.MANUAL, ""),
        createdBy = "",
        version = 1,
    )
}
