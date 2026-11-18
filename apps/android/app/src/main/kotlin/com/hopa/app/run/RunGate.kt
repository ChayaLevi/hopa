package com.hopa.app.run

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * One owner for "who is running on this phone": chat, a delivered task, or
 * nothing. The claim is atomic; a second caller is refused instead of fighting
 * over the same screen.
 */
class RunGate {
    private val lock = Any()
    private val _state = MutableStateFlow(RunState())
    val state: StateFlow<RunState> = _state.asStateFlow()

    /** True when this caller now owns the phone; false when someone else does. */
    fun claim(goal: String, source: RunSource): Boolean = synchronized(lock) {
        if (_state.value.busy) {
            false
        } else {
            _state.value = RunState(goal = goal, source = source)
            true
        }
    }

    /** Asked by the person; the turn checks it between steps. */
    fun cancel() {
        _state.update { it.copy(cancelRequested = true) }
    }

    fun finish() = synchronized(lock) {
        _state.value = RunState()
    }

    fun isCancelled(): Boolean = _state.value.cancelRequested
}
