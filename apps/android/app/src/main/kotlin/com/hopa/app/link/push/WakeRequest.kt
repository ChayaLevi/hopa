package com.hopa.app.link.push

/**
 * The two ids a wake message may carry. The rest of the payload is never
 * rendered: only these travel past the parser.
 */
data class WakeRequest(val taskId: String, val traceId: String)
