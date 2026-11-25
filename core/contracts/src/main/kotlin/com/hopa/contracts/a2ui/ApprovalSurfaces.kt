package com.hopa.contracts.a2ui

import com.hopa.contracts.approval.ApprovalRequest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Approval cards (ADR-0031). The only code that writes one: deterministic, from
 * the typed [ApprovalRequest], never from a model. The typed request stays the
 * source of truth; the card only shows it.
 *
 * `highlight` and `rememberPackage` stay local to the acting device: a screen
 * region means nothing on another device, and "remember this app" is a
 * setting of the device that acts.
 */
object ApprovalSurfaces {
    const val APPROVE = "approve"
    const val DENY = "deny"
    const val ALLOW_ID = "allow"
    const val DENY_ID = "deny"
    const val ACKNOWLEDGE = "I understand money may leave my account."
    const val ALLOW_LABEL = "Allow"
    const val DENY_LABEL = "Deny"

    fun surfaceId(task: String, n: Int): String = A2ui.surfaceId("appr", task, n)

    /** One `createSurface` with the components and the data model inline. */
    fun build(request: ApprovalRequest, surfaceId: String): List<A2uiMessage> {
        val content = mutableListOf("title", "body")
        val parts = mutableListOf<A2uiComponent>(
            A2uiComponent.Text("title", bound("/approval/title")),
            A2uiComponent.Text("body", bound("/approval/body")),
        )
        val approval = linkedMapOf("title" to request.title, "body" to request.body)
        val payment = linkedMapOf<String, String>()
        if (request.isPayment) {
            payment["amount"] = request.amount
            if (request.merchant.isNotEmpty()) payment["merchant"] = request.merchant
            if (request.consequence.isNotEmpty()) payment["consequence"] = request.consequence
            parts += A2uiComponent.PaymentNotice(
                id = "notice",
                amount = bound("/payment/amount"),
                acknowledge = A2uiText.Literal(ACKNOWLEDGE),
                merchant = bound("/payment/merchant").takeIf { "merchant" in payment },
                consequence = bound("/payment/consequence").takeIf { "consequence" in payment },
            )
            content += "notice"
        } else {
            if (request.amount.isNotEmpty()) {
                approval["amountLine"] = "Amount: ${request.amount}"
                parts += A2uiComponent.Text("amountLine", bound("/approval/amountLine"))
                content += "amountLine"
            }
            if (request.merchant.isNotEmpty()) {
                approval["merchantLine"] = "Merchant: ${request.merchant}"
                parts += A2uiComponent.Text("merchantLine", bound("/approval/merchantLine"))
                content += "merchantLine"
            }
        }
        approval["actionLine"] = "Action: ${request.action.kind}"
        parts += A2uiComponent.Text("actionLine", bound("/approval/actionLine"), variant = "caption")
        content += listOf("actionLine", "buttons")
        val components = listOf(
            A2uiComponent.Card(A2uiSurface.ROOT, "content"),
            A2uiComponent.Column("content", content),
        ) + parts + listOf(
            A2uiComponent.Row("buttons", listOf(DENY_ID, ALLOW_ID), justify = "end"),
            A2uiComponent.Button(DENY_ID, "denyLabel", A2uiEvent(DENY), variant = "borderless"),
            A2uiComponent.Text("denyLabel", A2uiText.Literal(DENY_LABEL)),
            A2uiComponent.Button(ALLOW_ID, "allowLabel", A2uiEvent(APPROVE), variant = "primary"),
            A2uiComponent.Text("allowLabel", A2uiText.Literal(ALLOW_LABEL)),
        )
        val model = buildJsonObject {
            put("approval", strings(approval))
            if (request.isPayment) put("payment", strings(payment))
        }
        return listOf(A2uiMessage.CreateSurface(surfaceId, components, model))
    }

    /** The card as it travels in `task.needs_approval`: [build]'s messages, encoded, in send order. */
    fun wire(request: ApprovalRequest, surfaceId: String): JsonArray =
        JsonArray(build(request, surfaceId).map(A2uiCodec::encode))

    /**
     * HOPA's rules for any approval surface it is asked to draw, on top of the
     * A2UI rules: only approve and deny, exactly one of each, no context, and a
     * PaymentNotice on a payment. Returns why it fails, or null.
     */
    fun problem(surface: A2uiSurface, isPayment: Boolean): String? {
        val buttons = surface.components.values.filterIsInstance<A2uiComponent.Button>()
        val notices = surface.components.values.count { it is A2uiComponent.PaymentNotice }
        return when {
            buttons.any { it.event.name != APPROVE && it.event.name != DENY } ->
                "an approval card only has approve and deny buttons"
            buttons.count { it.event.name == APPROVE } != 1 -> "an approval card needs exactly one approve button"
            buttons.count { it.event.name == DENY } != 1 -> "an approval card needs exactly one deny button"
            buttons.any { it.event.context.isNotEmpty() } -> "approval buttons carry no context"
            isPayment && notices != 1 -> "a payment card needs one PaymentNotice"
            else -> null
        }
    }

    /**
     * Whether a card someone else sent shows the typed [request] it came with
     * (ADR-0031, "a sent card is checked against the request"). The sender
     * decides the layout; consent decides these: the title and body are shown,
     * the money is the request's money, and the buttons say what they do.
     * Call it on a surface that already passed [problem]. Returns why it
     * fails, or null.
     */
    fun matches(surface: A2uiSurface, request: ApprovalRequest): String? {
        val parts = surface.components.values
        val shown = parts.filterIsInstance<A2uiComponent.Text>().map { surface.text(it.text) }
        val notices = parts.filterIsInstance<A2uiComponent.PaymentNotice>()
        val content = when {
            request.title.isNotEmpty() && request.title !in shown -> "the card does not show the request's title"
            request.body.isNotEmpty() && request.body !in shown -> "the card does not show the request's body"
            request.isPayment -> notices.singleOrNull()?.let { notice(surface, it, request) }
                ?: "a payment card needs one PaymentNotice".takeIf { notices.size != 1 }
            notices.isNotEmpty() -> "a card that is not a payment has no PaymentNotice"
            request.amount.isNotEmpty() && shown.none { request.amount in it } ->
                "the card does not show the request's amount"
            request.merchant.isNotEmpty() && shown.none { request.merchant in it } ->
                "the card does not show the request's merchant"
            else -> null
        }
        return content ?: buttonProblem(surface, parts.filterIsInstance<A2uiComponent.Button>())
    }

    /**
     * A card sent with a `task.needs_approval`, checked before anything is
     * drawn: the A2UI rules, then [problem], then [matches]. One card is one
     * surface. Anything else is [ApprovalCard.Refused], which the receiver
     * answers with deny.
     */
    fun receive(a2ui: JsonElement, request: ApprovalRequest): ApprovalCard {
        val array = a2ui as? JsonArray ?: return refused("a2ui is an array of messages")
        val messages = array.map { it as? JsonObject ?: return refused("a card message is an object") }
        val session = A2uiSession()
        var outcome: A2uiOutcome = A2uiOutcome.Pending("")
        val ids = mutableSetOf<String>()
        for (message in messages) {
            outcome = session.apply(message)
            when (val step = outcome) {
                is A2uiOutcome.Refused -> return ApprovalCard.Refused(
                    step.problem.message,
                    step.surfaceId?.let { A2uiCodec.errorFor(it, step.problem) },
                )
                is A2uiOutcome.Ready -> ids += step.surface.id
                is A2uiOutcome.Pending -> ids += step.surfaceId
                is A2uiOutcome.Deleted -> ids += step.surfaceId
            }
        }
        if (ids.size > 1) return refused("an approval card is one surface")
        val surface = (outcome as? A2uiOutcome.Ready)?.surface ?: return refused("the card never became complete")
        val reason = problem(surface, request.isPayment) ?: matches(surface, request)
            ?: return ApprovalCard.Shown(surface)
        return ApprovalCard.Refused(
            reason,
            A2uiCodec.errorFor(surface.id, A2uiProblem(A2uiCode.VALIDATION_FAILED, "", reason)),
        )
    }

    /**
     * [allowed] for an answer as it arrives in `task.result`: [action] is the
     * raw `body.approval.action`, an A2UI `action` message. Absent means a
     * client without A2UI; anything present that is not a valid `action` is deny.
     */
    fun answered(allow: Boolean, action: JsonElement?, surfaceId: String): Boolean {
        if (action == null || action is JsonNull) return allowed(allow, null, surfaceId)
        val decoded = (action as? JsonObject)?.let(A2uiCodec::decodeReply) as? A2uiResult.Ok ?: return false
        val tap = decoded.value as? A2uiAction ?: return false
        return allowed(allow, tap, surfaceId)
    }

    /**
     * The acting device's reading of a `task.result` answer. The typed [allow]
     * decides; an A2UI [action], when present, must agree with it, come from
     * the allow button and name the surface that was sent. Anything else is
     * deny. A client without A2UI sends no action and keeps today's path.
     */
    fun allowed(allow: Boolean, action: A2uiAction?, surfaceId: String): Boolean {
        if (!allow) return false
        if (action == null) return true
        return action.name == APPROVE && action.sourceComponentId == ALLOW_ID && action.surfaceId == surfaceId
    }

    private fun notice(surface: A2uiSurface, notice: A2uiComponent.PaymentNotice, request: ApprovalRequest): String? {
        fun shows(value: A2uiText?) = value?.let(surface::text).orEmpty()
        return when {
            shows(notice.amount) != request.amount -> "the PaymentNotice does not show the request's amount"
            shows(notice.merchant) != request.merchant -> "the PaymentNotice does not show the request's merchant"
            shows(notice.consequence) != request.consequence ->
                "the PaymentNotice does not show the request's consequence"
            shows(notice.acknowledge) != ACKNOWLEDGE -> "the PaymentNotice asks for HOPA's own acknowledgement"
            else -> null
        }
    }

    /** The buttons say what they do: fixed labels, and no accessibility text that could say otherwise. */
    private fun buttonProblem(surface: A2uiSurface, buttons: List<A2uiComponent.Button>): String? {
        fun label(button: A2uiComponent.Button?) =
            (button?.let { surface.component(it.child) } as? A2uiComponent.Text)?.let { surface.text(it.text) }
        val approve = buttons.singleOrNull { it.event.name == APPROVE }
        val deny = buttons.singleOrNull { it.event.name == DENY }
        return when {
            label(approve) != ALLOW_LABEL -> "the approve button reads $ALLOW_LABEL"
            label(deny) != DENY_LABEL -> "the deny button reads $DENY_LABEL"
            buttons.any { it.accessibility != null || surface.component(it.child)?.accessibility != null } ->
                "approval buttons carry no accessibility text of their own"
            else -> null
        }
    }

    private fun refused(reason: String) = ApprovalCard.Refused(reason, null)

    private fun bound(path: String) = A2uiText.Bound(path)

    private fun strings(values: Map<String, String>): JsonObject = buildJsonObject {
        values.forEach { (key, value) -> put(key, value) }
    }
}
