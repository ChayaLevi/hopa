package com.hopa.data.store

import com.hopa.contracts.Action
import com.hopa.contracts.ApprovalRequest
import com.hopa.contracts.json.HopaJson
import com.hopa.data.database.dao.PendingApprovalDao
import com.hopa.data.database.entity.PendingApprovalEntity
import com.hopa.stores.PendingApprovalStore
import java.util.UUID
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

class DbPendingApprovalStore(
    private val dao: PendingApprovalDao,
) : PendingApprovalStore {
    private var current: String? = null

    override suspend fun save(request: ApprovalRequest) {
        val id = UUID.randomUUID().toString()
        current = id
        dao.insert(
            PendingApprovalEntity(
                id = id,
                title = request.title,
                body = request.body,
                amount = request.amount,
                merchant = request.merchant,
                consequence = request.consequence,
                isPayment = request.isPayment,
                actionJson = HopaJson.element
                    .encodeToJsonElement(Action.serializer(), request.action)
                    .toString(),
                createdAt = System.currentTimeMillis(),
                highlightJson = request.highlight?.joinToString(",").orEmpty(),
                rememberPackage = request.rememberPackage.orEmpty(),
            ),
        )
    }

    override suspend fun clear() {
        current?.let { dao.delete(it) }
        current = null
    }

    override suspend fun clearAll() {
        current = null
        dao.deleteAll()
    }

    override suspend fun all(): List<ApprovalRequest> = dao.all().map { row ->
        ApprovalRequest(
            title = row.title,
            body = row.body,
            amount = row.amount,
            merchant = row.merchant,
            consequence = row.consequence,
            isPayment = row.isPayment,
            action = HopaJson.element.decodeFromJsonElement(
                Action.serializer(),
                HopaJson.element.parseToJsonElement(row.actionJson),
            ),
            highlight = row.highlightJson
                .split(',')
                .mapNotNull { it.toIntOrNull() }
                .takeIf { it.size == 4 },
            rememberPackage = row.rememberPackage.takeIf { it.isNotEmpty() },
        )
    }
}
