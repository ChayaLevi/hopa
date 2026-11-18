package com.hopa.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Action(
    @SerialName("schemaVersion") val schemaVersion: String = "1",
    val kind: String,
    val ref: String? = null,
    val text: String? = null,
    val x: Int? = null,
    val y: Int? = null,
    @SerialName("toX") val toX: Int? = null,
    @SerialName("toY") val toY: Int? = null,
    val app: String? = null,
    val url: String? = null,
    val direction: String? = null,
    @SerialName("timeoutMs") val timeoutMs: Int = 3000,
) {
    fun kindOrNull(): ActionKind? = ActionKind.fromWire(kind)
}

enum class ActionKind(val wire: String) {
    UI_OBSERVE("ui.observe"),
    UI_TAP("ui.tap"),
    UI_DOUBLE_TAP("ui.doubleTap"),
    UI_LONG_PRESS("ui.longPress"),
    UI_TYPE("ui.type"),
    UI_SWIPE("ui.swipe"),
    UI_SCROLL("ui.scroll"),
    UI_BACK("ui.back"),
    UI_HOME("ui.home"),
    UI_LAUNCH("ui.launch"),
    UI_STOP("ui.stop"),
    UI_WAIT("ui.wait"),
    APP_FUNCTION("app.function"),
    WEB_NAVIGATE("web.navigate"),
    WEB_READ("web.read"),
    WEB_CLICK("web.click"),
    WEB_TYPE("web.type"),
    SYSTEM_SET_ALARM("system.setAlarm"),
    SYSTEM_ADD_EVENT("system.addEvent"),
    SYSTEM_OPEN_LINK("system.openLink"),
    SYSTEM_SHARE("system.share"),
    SYSTEM_SEND_MESSAGE("system.sendMessage"),
    SYSTEM_CALL("system.call"),
    SYSTEM_DELETE("system.delete"),
    SYSTEM_UNINSTALL("system.uninstall"),
    SYSTEM_RESET("system.reset"),
    SANDBOX_RUN("sandbox.run"),
    PAYMENT_PAY("payment.pay"),
    PAYMENT_CONFIRM("payment.confirm"),
    PAYMENT_CHECKOUT("payment.checkout"),
    PAYMENT_ENTER_CARD("payment.enterCard"),
    PAYMENT_AUTHORIZE("payment.authorize"),
    ;

    companion object {
        fun fromWire(value: String): ActionKind? = entries.firstOrNull { it.wire == value }
    }
}
