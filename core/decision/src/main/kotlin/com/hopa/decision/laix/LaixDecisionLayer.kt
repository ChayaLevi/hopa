package com.hopa.decision.laix

import com.hopa.decision.ActionClass
import com.hopa.decision.ActionSignature
import com.hopa.decision.ConformanceStatus
import com.hopa.decision.DecisionContext
import com.hopa.decision.DecisionHistory
import com.hopa.decision.DecisionLayer
import com.hopa.decision.LatencyClass
import com.hopa.decision.LayerDecision
import com.hopa.decision.LayerMetadata
import com.hopa.decision.LayerStatus
import dev.laix.decision.contracts.ActionOption
import dev.laix.decision.contracts.Situation
import dev.laix.decision.contracts.Verb
import dev.laix.decision.policies.action.ActionPolicy
import dev.laix.decision.policies.action.ActionPolicyInput
import dev.laix.decision.support.iso

/**
 * LAIX 0.18 as a decision layer: the vendored action policy, fed with HOPA's
 * facts, answers in our own vocabulary.
 *
 * The translation is deliberately conservative. A read is confident and urgent;
 * a change is uncertain enough to ask; money and destruction are never offered
 * as an ACT option at all. "Look before asking" becomes VERIFY, "guide" becomes
 * ASK, because HOPA cannot guide a person in the middle of a step.
 *
 * No LAIX type leaves this file: the loop sees a [LayerDecision].
 */
class LaixDecisionLayer(
    private val policy: ActionPolicy = ActionPolicy(),
) : DecisionLayer {
    override val metadata: LayerMetadata = LayerMetadata(
        id = "laix",
        version = LAIX_VERSION,
        latency = LatencyClass.MICRO,
        decidesUpTo = ActionClass.DANGEROUS,
        status = LayerStatus.SHADOW,
        conformance = ConformanceStatus.PASSED,
        author = "LAIX $LAIX_VERSION (vendored, Apache-2.0)",
        summary = "Live decision policy: wait, observe, ask, act, verify or escalate with a reason and a score.",
    )

    override suspend fun decide(context: DecisionContext): LayerDecision {
        val decision = policy.decide(input(context))
        val chosen = decision.actionId?.takeIf { id ->
            context.candidates.any { it.id == id }
        }
        return LayerDecision(
            verb = LaixVerbs.toHopa(decision.verb),
            reason = "laix.${decision.verb.value}",
            confidence = decision.confidence,
            trace = "laix ${policy.version}: ${decision.rationale}",
            chosen = chosen,
        )
    }

    private fun input(context: DecisionContext): ActionPolicyInput {
        val situation = Situation(
            id = "hopa-${context.runId}-${context.step}",
            at = iso(),
            subject = "hopa/${context.runId}",
            attributes = mapOf(
                "action" to context.action?.kind.orEmpty(),
                "action_class" to context.actionClass.name.lowercase(),
                "signature" to context.signature,
            ),
        )
        return ActionPolicyInput(
            situation = situation,
            options = options(context),
            attributes = mapOf(
                "confidence" to confidence(context),
                "urgency" to urgency(context),
                "needs_more_info" to needsMoreInfo(context),
                "verification_due" to verificationDue(context),
            ),
        )
    }

    private fun options(context: DecisionContext): List<ActionOption> {
        if (context.candidates.size > 1) {
            return candidateOptions(context)
        }
        val options = mutableListOf<ActionOption>()
        if (canAct(context)) {
            val change = context.actionClass == ActionClass.CHANGE
            options += ActionOption(
                id = "act",
                verb = Verb.ACT,
                name = context.action?.kind.orEmpty(),
                params = mapOf("signature" to context.signature),
                expectedBenefit = 0.8,
                cost = 0.1,
                risk = risk(context.actionClass),
                latencyMs = 250,
                interruptCost = interrupt(context.actionClass),
                destructive = false,
                reversible = true,
                requiresApproval = change && context.signature !in context.approvals,
                rationale = "the step the model proposed",
            )
        }
        if (!context.atStepLimit) {
            options += ActionOption(
                id = "wait",
                verb = Verb.WAIT,
                name = "wait",
                expectedBenefit = 0.2,
                risk = 0.0,
                reversible = true,
                rationale = "let the situation settle",
            )
        }
        options += ActionOption(
            id = "ask",
            verb = Verb.ASK,
            name = "ask the person",
            expectedBenefit = 0.45,
            risk = 0.0,
            interruptCost = 0.5,
            reversible = true,
            rationale = "only the person knows the intent behind a change",
        )
        if (observable(context)) {
            options += ActionOption(
                id = "observe",
                verb = Verb.OBSERVE,
                name = "read the screen",
                expectedBenefit = 0.3,
                cost = 0.1,
                risk = 0.0,
                reversible = true,
                rationale = "a cheap check may settle the doubt",
            )
        }
        if (verificationDue(context)) {
            options += ActionOption(
                id = "verify",
                verb = Verb.VERIFY,
                name = "check the outcome",
                expectedBenefit = 0.4,
                risk = 0.0,
                reversible = true,
                rationale = "the previous step has no confirmed result",
            )
        }
        return options
    }

    /**
     * One ACT option per usable edge, plus the same ask/wait/observe choices a
     * single-step turn has. Dangerous edges are not offered at all.
     */
    private fun candidateOptions(context: DecisionContext): List<ActionOption> {
        val options = mutableListOf<ActionOption>()
        context.candidates
            .filter { it.actionClass != ActionClass.DANGEROUS && it.failures < 2 }
            .forEach { candidate ->
                val change = candidate.actionClass == ActionClass.CHANGE
                options += ActionOption(
                    id = candidate.id,
                    verb = Verb.ACT,
                    name = candidate.describe(),
                    params = mapOf("signature" to ActionSignature.of(candidate.action)),
                    expectedBenefit = 0.8,
                    cost = 0.1,
                    risk = risk(candidate.actionClass),
                    latencyMs = 250,
                    interruptCost = interrupt(candidate.actionClass),
                    reversible = true,
                    requiresApproval = change,
                    rationale = "an edge from the current screen",
                )
            }
        if (!context.atStepLimit) {
            options += ActionOption(
                id = "wait",
                verb = Verb.WAIT,
                name = "wait",
                expectedBenefit = 0.2,
                risk = 0.0,
                reversible = true,
                rationale = "let the situation settle",
            )
        }
        options += ActionOption(
            id = "ask",
            verb = Verb.ASK,
            name = "ask the person",
            expectedBenefit = 0.45,
            risk = 0.0,
            interruptCost = 0.5,
            reversible = true,
            rationale = "the edges are equally plausible",
        )
        if (needsMoreInfo(context)) {
            options += ActionOption(
                id = "observe",
                verb = Verb.OBSERVE,
                name = "read the screen",
                expectedBenefit = 0.3,
                cost = 0.1,
                risk = 0.0,
                reversible = true,
                rationale = "a cheap check may settle the doubt",
            )
        }
        return options
    }

    private fun canAct(context: DecisionContext): Boolean = context.action != null &&
        !context.cancelled &&
        !context.atStepLimit &&
        !context.attention.isSpent() &&
        context.actionClass != ActionClass.DANGEROUS

    /**
     * Looking is useful only when the screen may answer the question. For a
     * change the missing information is the person's intent, so asking is the
     * next step, not reading; for a payment or a delete, nothing on the screen
     * makes it safe.
     */
    private fun observable(context: DecisionContext): Boolean = context.action != null &&
        !context.cancelled &&
        context.actionClass != ActionClass.DANGEROUS &&
        (context.actionClass == ActionClass.READ || needsMoreInfo(context) || verificationDue(context))

    private fun needsMoreInfo(context: DecisionContext): Boolean =
        context.history.lastOrNull() == DecisionHistory.NO_CHANGE

    private fun verificationDue(context: DecisionContext): Boolean =
        context.history.count { it == context.signature } >= 2

    private fun confidence(context: DecisionContext): Double = when {
        context.cancelled -> 0.9
        context.atStepLimit -> 0.6
        // With no interruptions left, waiting is the confident answer.
        context.attention.isSpent() -> 0.8
        context.candidates.size > 1 -> candidateConfidence(context)
        context.action == null -> 0.4
        context.signature in context.approvals -> 0.9
        verificationDue(context) -> 0.75
        context.actionClass == ActionClass.READ -> 0.95
        context.actionClass == ActionClass.CHANGE -> 0.5
        else -> 0.2
    }

    private fun urgency(context: DecisionContext): Double = when {
        context.cancelled -> 0.1
        context.atStepLimit -> 0.2
        context.attention.isSpent() -> 0.4
        context.candidates.size > 1 -> candidateUrgency(context)
        context.signature in context.approvals -> 0.8
        verificationDue(context) -> 0.6
        context.actionClass == ActionClass.READ -> 0.8
        context.actionClass == ActionClass.CHANGE -> 0.55
        else -> 0.2
    }

    private fun candidateConfidence(context: DecisionContext): Double {
        val safe = context.candidates.filter {
            it.actionClass != ActionClass.DANGEROUS && it.failures < 2
        }
        return when {
            safe.isEmpty() -> 0.4
            safe.size == 1 && safe.single().actionClass == ActionClass.READ -> 0.95
            safe.size == 1 -> 0.5
            else -> 0.5
        }
    }

    private fun candidateUrgency(context: DecisionContext): Double {
        val safe = context.candidates.filter {
            it.actionClass != ActionClass.DANGEROUS && it.failures < 2
        }
        return when {
            safe.size == 1 && safe.single().actionClass == ActionClass.READ -> 0.8
            else -> 0.5
        }
    }

    private fun risk(actionClass: ActionClass): Double = when (actionClass) {
        ActionClass.READ -> 0.05
        ActionClass.CHANGE -> 0.35
        ActionClass.DANGEROUS -> 0.95
    }

    private fun interrupt(actionClass: ActionClass): Double = when (actionClass) {
        ActionClass.READ -> 0.0
        ActionClass.CHANGE -> 0.35
        ActionClass.DANGEROUS -> 1.0
    }

    private companion object {
        const val LAIX_VERSION = "0.18.0"
    }
}
