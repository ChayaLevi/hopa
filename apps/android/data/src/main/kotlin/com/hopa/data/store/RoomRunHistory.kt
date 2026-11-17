package com.hopa.data.store

import com.hopa.data.database.dao.RunHistoryDao
import com.hopa.data.database.entity.RunHistoryEntity
import java.util.UUID

/** Run history: one row per finished turn, so surfaces can be compared later. */
class RoomRunHistory(private val dao: RunHistoryDao) {
    suspend fun record(
        at: Long,
        goal: String,
        path: String,
        steps: Int,
        tokens: Int,
        conversationId: String,
    ) {
        dao.insert(
            RunHistoryEntity(
                id = UUID.randomUUID().toString(),
                at = at,
                goal = goal.take(200),
                path = path,
                steps = steps,
                tokens = tokens,
                conversationId = conversationId,
            ),
        )
        dao.prune()
    }

    suspend fun latest(path: String? = null): List<RunHistoryEntity> =
        if (path.isNullOrBlank()) dao.latest() else dao.byPath(path)

    suspend fun clear() = dao.clear()
}
