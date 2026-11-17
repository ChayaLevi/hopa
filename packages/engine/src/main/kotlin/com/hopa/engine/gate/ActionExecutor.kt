package com.hopa.engine.gate

import com.hopa.engine.contracts.Action
import com.hopa.engine.contracts.ErrorCodes
import com.hopa.engine.contracts.Observation
import com.hopa.engine.contracts.RiskLevel
import com.hopa.engine.observation.ObservationHasher
import com.hopa.engine.ports.BrowserPort
import com.hopa.engine.ports.Clock
import com.hopa.engine.ports.DevicePort
import com.hopa.engine.ports.SandboxPort
import com.hopa.engine.ports.SystemPort
import com.hopa.engine.records.RunRecordFactory
import com.hopa.engine.records.RunRecorder

class ActionExecutor(
    private val device: DevicePort,
    private val browser: BrowserPort?,
    private val system: SystemPort?,
    private val sandbox: SandboxPort?,
    private val policy: ApprovalPolicy,
    private val approval: ApprovalGateway,
    private val recorder: RunRecorder,
    private val factory: RunRecordFactory,
    private val clock: Clock,
    private val scrollGeometry: ScrollGeometry = ScrollGeometry(),
) {
    suspend fun execute(
        runId: String,
        step: Int,
        traceId: String,
        action: Action,
        before: Observation,
    ): ExecutionResult {
        val classification = policy.classify(action, before)
        val needsApproval = policy.requiresApproval(action, before)
        val approved = if (needsApproval) {
            approval.approve(policy.request(action, before))
        } else {
            null
        }
        val beforeHash = ObservationHasher.hash(before)

        if (needsApproval && approved != true) {
            val record = factory.draft(
                runId = runId,
                step = step,
                traceId = traceId,
                action = action,
                classification = label(classification),
                approved = false,
                observation = before,
                beforeHash = beforeHash,
                afterHash = beforeHash,
                verified = false,
                executed = false,
                error = ErrorCodes.REFUSED,
            ).let { recorder.append(it) }
            return ExecutionResult(record, before, "I did not run that.")
        }

        return runCatching {
            val text = executeAction(action, before)
            val after = runCatching { device.observe() }.getOrDefault(before)
            val changed = ObservationHasher.hash(after) != beforeHash
            val verified = text.isNotEmpty() || action.kind in setOf(
                "ui.observe", "ui.wait", "ui.back", "ui.home", "ui.stop",
            ) || changed
            val record = factory.draft(
                runId = runId,
                step = step,
                traceId = traceId,
                action = action,
                classification = label(classification),
                approved = approved,
                observation = after,
                beforeHash = beforeHash,
                afterHash = ObservationHasher.hash(after),
                verified = verified,
                executed = true,
            ).let { recorder.append(it) }
            ExecutionResult(record, after, text)
        }.getOrElse { error ->
            val code = if (error is UnsupportedOperationException) {
                ErrorCodes.UNSUPPORTED
            } else {
                ErrorCodes.DEVICE
            }
            val record = factory.draft(
                runId = runId,
                step = step,
                traceId = traceId,
                action = action,
                classification = label(classification),
                approved = approved,
                observation = before,
                beforeHash = beforeHash,
                afterHash = beforeHash,
                verified = false,
                executed = false,
                error = code,
            ).let { recorder.append(it) }
            ExecutionResult(record, before, "I could not do that: ${error.message ?: code}")
        }
    }

    private suspend fun executeAction(action: Action, before: Observation): String {
        when (action.kind) {
            "ui.observe" -> return ""
            "ui.tap" -> device.tap(action.ref, action.x, action.y)
            "ui.doubleTap" -> device.doubleTap(action.ref, action.x, action.y)
            "ui.longPress" -> device.longPress(action.ref, action.x, action.y)

            "ui.type" -> device.type(action.text.orEmpty(), action.ref)
            "ui.swipe" -> device.swipe(
                action.x ?: 500,
                action.y ?: 1500,
                action.toX ?: 500,
                action.toY ?: 500,
            )

            "ui.scroll" -> {
                val points = if (action.x != null && action.y != null) {
                    intArrayOf(
                        action.x,
                        action.y,
                        action.toX ?: action.x,
                        action.toY ?: action.y,
                    )
                } else {
                    scrollGeometry.swipe(before.screenSize, action.direction.orEmpty())
                }
                device.swipe(points[0], points[1], points[2], points[3])
            }

            "ui.back" -> device.pressBack()
            "ui.home" -> device.pressHome()
            "ui.launch" -> {
                val packageName = action.app?.takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException("ui.launch needs app")
                device.launch(packageName)
                return ""
            }

            "app.function" -> {
                val packageName = action.app?.takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException("app.function needs app")
                val function = action.ref?.takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException("app.function needs ref")
                return device.appFunction(
                    packageName,
                    function,
                    action.text.orEmpty().ifBlank { "{}" },
                )
            }

            "ui.stop" -> device.stop(
                action.app?.takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException("ui.stop needs app"),
            )

            "ui.wait" -> clock.sleep(action.timeoutMs.toLong())
            "web.navigate" -> {
                val port = browser ?: throw UnsupportedOperationException("no browser")
                return port.navigate(action.url ?: action.text.orEmpty())
            }

            "web.read" -> {
                val port = browser ?: throw UnsupportedOperationException("no browser")
                return port.read()
            }

            "web.click" -> {
                val port = browser ?: throw UnsupportedOperationException("no browser")
                port.click(action.ref ?: throw IllegalArgumentException("web.click needs ref"))
            }

            "web.type" -> {
                val port = browser ?: throw UnsupportedOperationException("no browser")
                port.type(action.text.orEmpty(), action.ref)
            }

            "system.openLink" -> {
                val port = system ?: throw UnsupportedOperationException("no system port")
                port.openLink(action.url ?: action.text.orEmpty())
            }

            "system.share" -> {
                val port = system ?: throw UnsupportedOperationException("no system port")
                port.share(action.text.orEmpty())
            }

            "system.sendMessage" -> {
                val port = system ?: throw UnsupportedOperationException("no system port")
                port.sendMessage(action.app.orEmpty(), action.text.orEmpty())
            }

            "system.call" -> {
                val port = system ?: throw UnsupportedOperationException("no system port")
                port.call(action.text.orEmpty())
            }

            "system.setAlarm" -> {
                val port = system ?: throw UnsupportedOperationException("no system port")
                val clockTime = parseClock(action.text.orEmpty())
                port.setAlarm(clockTime[0], clockTime[1], "")
            }

            "system.uninstall" -> {
                val port = system ?: throw UnsupportedOperationException("no system port")
                port.uninstall(action.app ?: action.text.orEmpty())
            }

            "sandbox.run" -> {
                val port = sandbox ?: throw UnsupportedOperationException("no sandbox")
                return port.run(action.text.orEmpty())
            }

            // A payment is still a device gesture: the tap or the card entry the
            // person approved. Without this, an approved payment could never run.
            "payment.pay", "payment.confirm", "payment.checkout", "payment.authorize" ->
                device.tap(action.ref, action.x, action.y)

            "payment.enterCard" -> device.type(action.text.orEmpty(), action.ref)

            else -> throw UnsupportedOperationException("unsupported kind: ${action.kind}")
        }
        return ""
    }

    /** "07:30" or "7:30" to hour and minute; noon when the text makes no sense. */
    private fun parseClock(text: String): IntArray {
        val match = Regex("(\\d{1,2}):(\\d{2})").find(text) ?: return intArrayOf(12, 0)
        return intArrayOf(
            match.groupValues[1].toInt().coerceIn(0, 23),
            match.groupValues[2].toInt().coerceIn(0, 59),
        )
    }

    private fun label(level: RiskLevel): String = when (level) {
        RiskLevel.READ -> "read"
        RiskLevel.SENSITIVE -> "sensitive"
        RiskLevel.PAYMENT -> "payment"
    }
}
