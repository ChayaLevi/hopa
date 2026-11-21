package com.hopa.decision

/**
 * A stable reason code. Codes are lower-case words joined by underscores, with
 * optional dotted groups for namespaces (`laix.policy_act`), so a run log can
 * be grouped without parsing prose.
 */
object ReasonCode {
    private val PATTERN = Regex("[a-z][a-z0-9_]*(\\.[a-z0-9_]+)*")

    fun matches(code: String): Boolean = code.length in 1..64 && PATTERN.matches(code)
}
