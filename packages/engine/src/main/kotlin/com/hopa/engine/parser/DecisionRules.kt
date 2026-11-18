package com.hopa.engine.parser

import com.hopa.contracts.ActionKind
import com.hopa.contracts.Decision
import com.hopa.contracts.ErrorCodes

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
        if (action != null && ActionKind.fromWire(action.kind) == null) {
            return ValidationResult.fail(
                ErrorCodes.SCHEMA,
                "unknown action kind: ${action.kind}",
            )
        }
        return ValidationResult.ok()
    }
}
