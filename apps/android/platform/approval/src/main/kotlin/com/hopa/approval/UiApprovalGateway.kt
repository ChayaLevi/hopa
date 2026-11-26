package com.hopa.approval

import com.hopa.contracts.a2ui.A2uiAction
import com.hopa.contracts.a2ui.A2uiSurface
import com.hopa.contracts.approval.ApprovalGateway
import com.hopa.contracts.approval.ApprovalRequest
import com.hopa.stores.PendingApprovalStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The approval question: shown in the sheet, persisted while it waits, denied when
 * nobody answers in time. One question at a time - parallel tasks queue here, so
 * two runs cannot overwrite each other's sheet or answer for one another.
 */
class UiApprovalGateway(
    private val store: PendingApprovalStore? = null,
    private val timeoutMillis: Long = 120_000,
) : ApprovalGateway {
    private val _question = MutableStateFlow<ApprovalQuestion?>(null)

    /** What the sheet draws: the request and, for another device's question, its checked card. */
    val question: StateFlow<ApprovalQuestion?> = _question.asStateFlow()

    private val _pending = MutableStateFlow<ApprovalRequest?>(null)

    /** The typed request alone, for screens that only need to know a question is open. */
    val pending: StateFlow<ApprovalRequest?> = _pending.asStateFlow()

    private var deferred: CompletableDeferred<ApprovalAnswer>? = null
    private val oneAtATime = Mutex()

    override suspend fun approve(request: ApprovalRequest): Boolean = ask(request).allow

    /** Shows [request], drawn from [card] when another device sent one, and waits for the person. */
    suspend fun ask(request: ApprovalRequest, card: A2uiSurface? = null): ApprovalAnswer = oneAtATime.withLock {
        val answer = CompletableDeferred<ApprovalAnswer>()
        deferred = answer
        _question.value = ApprovalQuestion(request, card)
        _pending.value = request
        runCatching { store?.save(request) }
        try {
            withTimeoutOrNull(timeoutMillis) { answer.await() } ?: ApprovalAnswer.DENY
        } finally {
            _pending.value = null
            _question.value = null
            deferred = null
            runCatching { store?.clear() }
        }
    }

    fun resolve(allow: Boolean, action: A2uiAction? = null) {
        deferred?.complete(ApprovalAnswer(allow, action))
    }
}
