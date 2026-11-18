package com.hopa.engine.parser

import com.hopa.contracts.Action
import com.hopa.contracts.ActionKind
import com.hopa.contracts.Decision
import com.hopa.contracts.ErrorCodes

/**
 * The shape a decision must have before the gate sees it. `DecisionParser` has
 * already checked the JSON; this checks that the action carries what its kind
 * needs, so a step that cannot work stops the model instead of reaching the
 * device and gesturing at a guess.
 *
 * The rules mirror `ActionExecutor`: a tap with no ref and no point, a launch
 * with no app, a swipe with half a gesture are all mistakes, not instructions.
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
        val missing = missing(kind, action)
        return if (missing == null) {
            ValidationResult.ok()
        } else {
            ValidationResult.fail(ErrorCodes.SCHEMA, "${action.kind} $missing")
        }
    }

    /** The field this kind cannot work without, or null when the action is whole. */
    private fun missing(kind: ActionKind, action: Action): String? = when (kind) {
        ActionKind.UI_TAP,
        ActionKind.UI_DOUBLE_TAP,
        ActionKind.UI_LONG_PRESS,
        ActionKind.PAYMENT_PAY,
        ActionKind.PAYMENT_CONFIRM,
        ActionKind.PAYMENT_CHECKOUT,
        ActionKind.PAYMENT_AUTHORIZE,
        ->
            if (action.hasPoint()) {
                null
            } else {
                "needs a ref, or x and y"
            }

        // Empty text is a real instruction: it clears the field.
        ActionKind.UI_TYPE,
        ActionKind.PAYMENT_ENTER_CARD,
        ->
            if (action.text == null) "needs text" else null

        ActionKind.UI_SWIPE ->
            if (action.x != null && action.y != null && action.toX != null && action.toY != null) {
                null
            } else {
                "needs x, y, toX and toY"
            }

        ActionKind.UI_SCROLL ->
            if (action.direction?.isNotBlank() == true || action.hasPoint()) {
                null
            } else {
                "needs a direction, or x and y"
            }

        ActionKind.UI_LAUNCH,
        ActionKind.UI_STOP,
        ->
            if (action.app.isNullOrBlank()) "needs app" else null

        ActionKind.APP_FUNCTION ->
            when {
                action.app.isNullOrBlank() -> "needs app"
                action.ref.isNullOrBlank() -> "needs the function name in ref"
                else -> null
            }

        ActionKind.WEB_NAVIGATE,
        ActionKind.SYSTEM_OPEN_LINK,
        ->
            if (action.url.isNullOrBlank() && action.text.isNullOrBlank()) {
                "needs a url"
            } else {
                null
            }

        ActionKind.WEB_CLICK -> if (action.ref.isNullOrBlank()) "needs ref" else null

        ActionKind.SYSTEM_UNINSTALL ->
            if (action.app.isNullOrBlank() && action.text.isNullOrBlank()) "needs app" else null

        ActionKind.SYSTEM_SET_ALARM ->
            if (action.text.isNullOrBlank()) "needs the time in text" else null

        ActionKind.SANDBOX_RUN ->
            if (action.text.isNullOrBlank()) "needs the code in text" else null

        ActionKind.UI_OBSERVE,
        ActionKind.UI_BACK,
        ActionKind.UI_HOME,
        ActionKind.UI_WAIT,
        ActionKind.WEB_READ,
        ActionKind.WEB_TYPE,
        ActionKind.SYSTEM_ADD_EVENT,
        ActionKind.SYSTEM_SHARE,
        ActionKind.SYSTEM_SEND_MESSAGE,
        ActionKind.SYSTEM_CALL,
        ActionKind.SYSTEM_DELETE,
        ActionKind.SYSTEM_RESET,
        ->
            null
    }

    private fun Action.hasPoint(): Boolean = !ref.isNullOrBlank() || (x != null && y != null)
}
