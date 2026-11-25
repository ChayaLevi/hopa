package com.hopa.approval

import com.hopa.contracts.a2ui.ApprovalCard
import com.hopa.contracts.a2ui.ApprovalSurfaces
import com.hopa.contracts.wire.Action
import com.hopa.contracts.approval.ApprovalRequest
import com.hopa.stores.PendingApprovalStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class UiApprovalGatewayTest {
    private val request = ApprovalRequest(
        title = "Confirm payment",
        body = "Money may leave your account.",
        amount = "25",
        merchant = "Coffee",
        consequence = "HOPA cannot undo a payment.",
        isPayment = true,
        action = Action(kind = "payment.checkout"),
    )

    @Test
    fun a_question_is_persisted_and_cleared_after_the_answer() = runBlocking {
        val store = FakeStore()
        val gateway = UiApprovalGateway(store, timeoutMillis = 5_000)
        var answer: Boolean? = null

        val job = launch { answer = gateway.approve(request) }
        yield()
        assertEquals(1, store.saved.size)

        gateway.resolve(true)
        job.join()

        assertEquals(true, answer)
        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun a_question_from_another_device_shows_its_card_and_returns_the_tap() = runBlocking {
        val gateway = UiApprovalGateway(FakeStore(), timeoutMillis = 5_000)
        val surfaceId = ApprovalSurfaces.surfaceId("ap-1", 1)
        val card = (ApprovalSurfaces.receive(ApprovalSurfaces.wire(request, surfaceId), request) as ApprovalCard.Shown)
            .surface
        var answer: ApprovalAnswer? = null

        val job = launch { answer = gateway.ask(request, card) }
        yield()
        assertEquals(ApprovalQuestion(request, card), gateway.question.value)
        assertEquals(request, gateway.pending.value)

        val tap = card.tap(ApprovalSurfaces.ALLOW_ID, Instant.EPOCH)
        gateway.resolve(true, tap)
        job.join()

        assertEquals(ApprovalAnswer(true, tap), answer)
        assertNull(gateway.question.value)
        assertNull(gateway.pending.value)
    }

    @Test
    fun an_unanswered_question_times_out_as_denied_and_is_cleared() = runBlocking {
        val store = FakeStore()
        val gateway = UiApprovalGateway(store, timeoutMillis = 100)

        assertFalse(gateway.approve(request))
        assertTrue(store.saved.isEmpty())
    }

    private class FakeStore : PendingApprovalStore {
        val saved = mutableListOf<ApprovalRequest>()

        override suspend fun save(request: ApprovalRequest) {
            saved += request
        }

        override suspend fun clear() {
            saved.clear()
        }

        override suspend fun clearAll() {
            saved.clear()
        }

        override suspend fun all(): List<ApprovalRequest> = saved.toList()
    }
}
