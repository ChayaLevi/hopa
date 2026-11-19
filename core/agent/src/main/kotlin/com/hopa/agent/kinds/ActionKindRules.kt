package com.hopa.agent.kinds

import com.hopa.contracts.wire.Action
import com.hopa.contracts.wire.ActionKind

/**
 * One table of what a kind needs and how it is treated, instead of the same
 * knowledge written twice: once in the parser's rules and once in the executor's
 * "needs app" guards. A new kind is added in `ActionKind` and here; the parser
 * rejects an incomplete step and the executor refuses to guess.
 */
object ActionKindRules {
    /** Reading the screen again is not a loop; acting on it twice is. */
    val READ_ONLY: Set<ActionKind> = setOf(
        ActionKind.UI_OBSERVE,
        ActionKind.UI_WAIT,
        ActionKind.WEB_READ,
    )

    /**
     * The field this kind cannot work without, or null when the action is whole.
     * The message is what the parser shows the model, so it names the missing
     * piece in the model's own words.
     */
    fun missing(kind: ActionKind, action: Action): String? = when (kind) {
        ActionKind.UI_TAP,
        ActionKind.UI_DOUBLE_TAP,
        ActionKind.UI_LONG_PRESS,
        ActionKind.PAYMENT_PAY,
        ActionKind.PAYMENT_CONFIRM,
        ActionKind.PAYMENT_CHECKOUT,
        ActionKind.PAYMENT_AUTHORIZE,
        -> if (action.hasPoint()) null else "needs a ref, or x and y"

        // Empty text is a real instruction: it clears the field.
        ActionKind.UI_TYPE,
        ActionKind.PAYMENT_ENTER_CARD,
        -> if (action.text == null) "needs text" else null

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
        -> if (action.app.isNullOrBlank()) "needs app" else null

        ActionKind.APP_FUNCTION ->
            when {
                action.app.isNullOrBlank() -> "needs app"
                action.ref.isNullOrBlank() -> "needs the function name in ref"
                else -> null
            }

        ActionKind.WEB_NAVIGATE,
        ActionKind.SYSTEM_OPEN_LINK,
        -> if (action.url.isNullOrBlank() && action.text.isNullOrBlank()) "needs a url" else null

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
        -> null
    }

    private fun Action.hasPoint(): Boolean = !ref.isNullOrBlank() || (x != null && y != null)
}
