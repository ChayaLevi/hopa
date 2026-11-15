package com.hopa.capabilities.display

import android.os.SystemClock
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Input for one virtual display. Injection needs shell privileges, so this is
 * only ever constructed inside the helper process; the reflection is the hidden
 * `InputManager.injectInputEvent` path every automation tool uses.
 */
class DisplayInput(
    private val displayId: Int,
) {
    private val inputManager: Any? = runCatching {
        val type = Class.forName("android.hardware.input.InputManager")
        type.getMethod("getInstance").invoke(null)
    }.getOrNull()

    private val inject = runCatching {
        Class.forName("android.hardware.input.InputManager")
            .getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
    }.getOrNull()

    private fun setDisplay(event: InputEvent) {
        runCatching {
            InputEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType)
                .invoke(event, displayId)
        }
    }

    private fun send(event: InputEvent): Boolean {
        setDisplay(event)
        val method = inject ?: return false
        val manager = inputManager ?: return false
        val result = runCatching { method.invoke(manager, event, INJECT_ASYNC) }
        return result.getOrNull() == true
    }

    fun tap(x: Int, y: Int): Boolean {
        val down = SystemClock.uptimeMillis()
        val events = listOf(
            MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat(), 0),
            MotionEvent.obtain(down, down + 60, MotionEvent.ACTION_UP, x.toFloat(), y.toFloat(), 0),
        )
        return try {
            events.all { send(it) }
        } finally {
            events.forEach { it.recycle() }
        }
    }

    fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, millis: Int): Boolean {
        val down = SystemClock.uptimeMillis()
        val start = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, fromX.toFloat(), fromY.toFloat(), 0)
        val move = MotionEvent.obtain(down, down + millis, MotionEvent.ACTION_MOVE, toX.toFloat(), toY.toFloat(), 0)
        val up = MotionEvent.obtain(down, down + millis + 60, MotionEvent.ACTION_UP, toX.toFloat(), toY.toFloat(), 0)
        return try {
            listOf(start, move, up).all { send(it) }
        } finally {
            listOf(start, move, up).forEach { it.recycle() }
        }
    }

    fun key(code: Int): Boolean {
        val down = SystemClock.uptimeMillis()
        val events = listOf(
            KeyEvent(down, down, KeyEvent.ACTION_DOWN, code, 0),
            KeyEvent(down, down + 40, KeyEvent.ACTION_UP, code, 0),
        )
        return events.all { send(it) }
    }

    private companion object {
        /** INJECT_INPUT_EVENT_MODE_ASYNC */
        const val INJECT_ASYNC = 0
    }
}
