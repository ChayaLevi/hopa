package com.hopa.decision.rules

import com.hopa.decision.ActionClass
import com.hopa.decision.ConformanceStatus
import com.hopa.decision.DecisionContext
import com.hopa.decision.DecisionHistory
import com.hopa.decision.DecisionLayer
import com.hopa.decision.LatencyClass
import com.hopa.decision.LayerDecision
import com.hopa.decision.LayerMetadata
import com.hopa.decision.LayerStatus
import com.hopa.decision.Verb

/**
 * The baseline layer: heuristics that need no model and no training.
 *
 * It is the fallback when no other layer is available, and the reference every
 * other layer has to beat. The rules are conservative on purpose: a read acts,
 * a change asks, a payment or a delete asks, a refusal is not retried, and a
 * cancelled turn never acts.
 */
class RulesDecisionLayer : DecisionLayer {
    override val metadata: LayerMetadata = LayerMetadata(
        id = "rules",
        version = VERSION,
        latency = LatencyClass.MICRO,
        decidesUpTo = ActionClass.DANGEROUS,
        status = LayerStatus.DEFAULT,
        conformance = ConformanceStatus.PASSED,
        author = "hopa",
        summary = "Heuristics for known commands; acts on reads, asks for changes, payments and deletes.",
    )

    override suspend fun decide(context: DecisionContext): LayerDecision {
        if (context.cancelled) {
            return decision(Verb.WAIT, "cancelled", 0.99, context)
        }
        if (context.atStepLimit) {
            return decision(Verb.ASK, "step_limit", 0.9, context)
        }
        val action = context.action ?: return decision(Verb.ASK, "no_action", 0.6, context)
        val kind = action.kindOrNull() ?: return decision(Verb.ASK, "unknown_action", 0.4, context)
        if (context.attention.isSpent()) {
            return decision(Verb.WAIT, "attention_spent", 0.8, context)
        }
        if (context.history.contains(DecisionHistory.DECLINED)) {
            return decision(Verb.ASK, "user_declined", 0.95, context)
        }
        if (context.history.count { it == context.signature } >= 2) {
            return decision(Verb.VERIFY, "repeated_step", 0.85, context)
        }
        if (context.history.lastOrNull() == DecisionHistory.NO_CHANGE) {
            return decision(Verb.VERIFY, "no_visible_change", 0.8, context)
        }
        if (context.signature in context.approvals) {
            return decision(Verb.ACT, "already_approved", 0.85, context)
        }
        return when (context.actionClass) {
            ActionClass.READ -> decision(Verb.ACT, "read_only", 0.95, context)

            ActionClass.DANGEROUS -> {
                val verb = if (kind.wire.startsWith("payment.")) {
                    decision(Verb.ASK, "payment_requires_approval", 0.99, context)
                } else {
                    decision(Verb.ASK, "destructive_requires_approval", 0.99, context)
                }
                verb
            }

            ActionClass.CHANGE -> decision(Verb.ASK, "needs_approval", 0.6, context)
        }
    }

    private fun decision(verb: Verb, reason: String, confidence: Double, context: DecisionContext): LayerDecision =
        LayerDecision(
            verb = verb,
            reason = reason,
            confidence = confidence,
            trace = "rules: ${context.actionClass.name.lowercase()} " +
                "${context.action?.kind ?: "none"} -> ${verb.name.lowercase()} ($reason)",
        )

    private companion object {
        const val VERSION = "1.0.0"
    }
}
