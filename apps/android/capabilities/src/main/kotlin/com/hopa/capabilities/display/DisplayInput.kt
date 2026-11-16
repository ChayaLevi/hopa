package com.hopa.capabilities.display

import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import java.lang.reflect.Method

/**
 * Input for one virtual display. Injection needs shell privileges, so this is
 * only ever constructed inside the helper process.
 *
 * Two doors, in order: the hidden `injectInputEvent` (fast, but its home and its
 * signature move between releases) and the shell's own `input` command with
 * `-d <display>` (slower, always present). A tap that cannot use the first must
 * not be reported as impossible when the second exists.
 */
class DisplayInput(
    private val displayId: Int,
) {
    private val inputManager: Any? = runCatching {
        Class.forName("android.hardware.input.InputManagerGlobal")
            .getMethod("getInstance")
            .invoke(null)
    }.getOrNull() ?: runCatching {
        Class.forName("android.hardware.input.InputManager")
            .getMethod("getInstance")
            .invoke(null)
    }.getOrNull()

    private val injectTwoArg: Method? = method(2)
    private val injectThreeArg: Method? = method(3)

    private fun method(arity: Int): Method? =
        inputManager?.javaClass?.methods?.firstOrNull { candidate ->
            candidate.name == "injectInputEvent" &&
                candidate.parameterCount == arity &&
                candidate.parameterTypes[0] == InputEvent::class.java &&
                candidate.parameterTypes.drop(1).all { it == Int::class.javaPrimitiveType }
        }

    private fun setDisplay(event: InputEvent) {
        runCatching {
            InputEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType)
                .invoke(event, displayId)
        }
    }

    /** The hidden path only; the caller falls back to the shell once per gesture. */
    private fun inject(event: InputEvent): Boolean {
        setDisplay(event)
        val manager = inputManager ?: return false
        val two = injectTwoArg
        val three = injectThreeArg
        val result = when {
            two != null -> runCatching { two.invoke(manager, event, INJECT_ASYNC) }
            three != null ->
                runCatching { three.invoke(manager, event, INJECT_ASYNC, TARGET_ALL_UIDS) }

            else -> return false
        }
        if (result.getOrNull() == true) {
            return true
        }
        result.exceptionOrNull()?.let { error ->
            System.err.println("input: inject refused: ${error.message}")
            System.err.flush()
        }
        return false
    }

    /** `input touchscreen -d <display> <command>`; the system's own injector. */
    private fun shell(command: String): Boolean = runCatching {
        val process = ProcessBuilder("sh", "-c", "input touchscreen -d $displayId $command")
            .redirectErrorStream(true)
            .start()
        val code = process.waitFor()
        if (code != 0) {
            val text = process.inputStream.bufferedReader().readText().take(160)
            System.err.println("input: '$command' failed ($code): $text")
            System.err.flush()
        }
        code == 0
    }.getOrDefault(false)

    fun tap(x: Int, y: Int): Boolean {
        val down = SystemClock.uptimeMillis()
        val events = listOf(
            motion(down, down, MotionEvent.ACTION_DOWN, x, y),
            motion(down, down + 60, MotionEvent.ACTION_UP, x, y),
        )
        return try {
            gesture(events, "tap $x $y")
        } finally {
            events.forEach { it.recycle() }
        }
    }

    fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, millis: Int): Boolean {
        val down = SystemClock.uptimeMillis()
        val start = motion(down, down, MotionEvent.ACTION_DOWN, fromX, fromY)
        val move = motion(down, down + millis, MotionEvent.ACTION_MOVE, toX, toY)
        val up = motion(down, down + millis + 60, MotionEvent.ACTION_UP, toX, toY)
        return try {
            gesture(listOf(start, move, up), "swipe $fromX $fromY $toX $toY $millis")
        } finally {
            listOf(start, move, up).forEach { it.recycle() }
        }
    }

    fun key(code: Int): Boolean {
        val down = SystemClock.uptimeMillis()
        val events = listOf(
            KeyEvent(down, down, KeyEvent.ACTION_DOWN, code, 0),
            KeyEvent(down, down + 40, KeyEvent.ACTION_UP, code, 0),
        ).onEach { it.source = InputDevice.SOURCE_KEYBOARD }
        return gesture(events, "keyevent $code")
    }

    /**
     * One path per gesture. If the down cannot be injected, the whole gesture
     * goes to the shell; switching paths halfway would send the down twice and
     * leave the display with a press that was never lifted.
     */
    private fun gesture(events: List<InputEvent>, command: String): Boolean {
        if (events.isEmpty()) {
            return false
        }
        if (!inject(events.first())) {
            return shell(command)
        }
        return events.drop(1).all { inject(it) }
    }

    /**
     * A touch event without a source is dropped by the dispatcher; the shell's own
     * `input` command sets it too.
     */
    private fun motion(down: Long, at: Long, action: Int, x: Int, y: Int): MotionEvent =
        MotionEvent.obtain(down, at, action, x.toFloat(), y.toFloat(), 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }

    private companion object {
        /** INJECT_INPUT_EVENT_MODE_ASYNC */
        const val INJECT_ASYNC = 0

        /** The uid-targeted overload delivers to whichever window owns the display. */
        const val TARGET_ALL_UIDS = -1
    }
}
