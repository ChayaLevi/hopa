package com.hopa.family.board

import com.hopa.family.model.Importance
import com.hopa.family.model.Recurrence

/** The fields a create or an edit sets. A null field is left as it is. */
internal data class Fields(
    val title: String? = null,
    val notes: String? = null,
    val start: String? = null,
    val end: String? = null,
    val allDay: Boolean? = null,
    val forMemberIds: List<String>? = null,
    val sensitive: Boolean? = null,
    val importance: Importance? = null,
    val unavailableMemberIds: List<String>? = null,
    val recurrence: Recurrence? = null,
    val clearRecurrence: Boolean = false,
)
