package com.hopa.sidecar.protocol.handler

import com.hopa.sidecar.approval.HostApproval
import com.hopa.sidecar.store.PhoneKeys
import com.hopa.sidecar.turn.RuntimeConfig
import com.hopa.sidecar.protocol.Protocol.text
import com.hopa.sidecar.protocol.Stdio
import kotlinx.serialization.json.JsonObject

/**
 * An approval answered by a device. ADR-0024: a phone answer cannot be trusted
 * when no phone link exists — there is no path for the phone to have answered,
 * so it is refused instead of believed (fail closed).
 *
 * `phonekeys.forget` (ADR-0028) is the Settings window's "Forget phone keys":
 * after a phone is paired again, its next signed answer pins its new key.
 */
internal class ApprovalHandler(
    private val io: Stdio,
    private val approval: HostApproval,
    private val config: RuntimeConfig,
    private val pins: PhoneKeys,
) : MessageHandler {
    override fun handle(type: String, message: JsonObject): Boolean {
        if (type == "phonekeys.forget") {
            io.log("forgot ${pins.forgetAll()} pinned phone key(s); the next signed answer pins again")
            return true
        }
        if (type != "approval.result") return false
        val id = message.text("id")
        val claimed = message.text("device").ifBlank { "pc" }
        val allow = message.text("allow").toBoolean()
        if (claimed == "phone" && !config.hasPhoneLink) {
            io.log("refused an approval claiming the phone: no phone link is attached")
            approval.resolve(id, false, "pc")
        } else {
            // ADR-0027/0028: what the phone sent (its sender, tap and signature); HostApproval checks it.
            approval.resolve(id, allow, claimed, message["phone"] as? JsonObject)
        }
        return true
    }
}
