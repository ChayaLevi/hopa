package com.hopa.decision

import com.hopa.contracts.wire.ActionKind

/**
 * How much is at stake in one action. The class, not the action kind, decides
 * which layers may speak and whether a person still has to approve.
 *
 * Unknown kinds are [DANGEROUS] on purpose: a new wire action must be classified
 * by hand before anything may act on it on its own.
 */
enum class ActionClass {
    READ,
    CHANGE,
    DANGEROUS,
    ;

    companion object {
        fun of(kind: ActionKind?): ActionClass = when (kind) {
            null -> DANGEROUS

            ActionKind.UI_OBSERVE,
            ActionKind.UI_BACK,
            ActionKind.UI_HOME,
            ActionKind.UI_WAIT,
            ActionKind.UI_STOP,
            ActionKind.UI_LAUNCH,
            ActionKind.UI_SCROLL,
            ActionKind.WEB_READ,
            -> READ

            ActionKind.SYSTEM_DELETE,
            ActionKind.SYSTEM_UNINSTALL,
            ActionKind.SYSTEM_RESET,
            ActionKind.PAYMENT_PAY,
            ActionKind.PAYMENT_CONFIRM,
            ActionKind.PAYMENT_CHECKOUT,
            ActionKind.PAYMENT_ENTER_CARD,
            ActionKind.PAYMENT_AUTHORIZE,
            -> DANGEROUS

            else -> CHANGE
        }
    }
}
