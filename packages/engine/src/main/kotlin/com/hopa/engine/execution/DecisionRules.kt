package com.hopa.engine.execution

import com.hopa.contracts.ErrorCodes
import com.hopa.contracts.wire.ActionKind
import com.hopa.contracts.wire.Decision

/**
 * The shape a decision must have before the gate sees it. `DecisionParser` has
 * already checked the JSON; this checks that the action carries what its kind
 * needs, by asking [ActionKindRules] - the same table the executor uses, so the
 * two cannot drift.
 */
object DecisionRules {
    fun validate(decision: Decision): ValidationResult {
        val action = decision.action
        if (decision.done && action != null) {
            return ValidationResult.fail(
                ErrorCodes.SCHEMA,
                "done=true cannot carry an action",
            )
        }
        if (decision.needUser && decision.options.isNullOrEmpty()) {
            return ValidationResult.fail(
                ErrorCodes.SCHEMA,
                "needUser=true requires options",
            )
        }
        if (action == null) {
            return ValidationResult.ok()
        }
        val kind = ActionKind.fromWire(action.kind)
            ?: return ValidationResult.fail(ErrorCodes.SCHEMA, "unknown action kind: ${action.kind}")
        val missing = ActionKindRules.missing(kind, action)
        return if (missing == null) {
            ValidationResult.ok()
        } else {
            ValidationResult.fail(ErrorCodes.SCHEMA, "${action.kind} $missing")
        }
    }
}
