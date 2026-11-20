package com.hopa.feature.history

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import com.hopa.feature.history.ActivityDependencies
import com.hopa.capabilities.accessibility.state.AccessibilityState
import com.hopa.contracts.wire.RunRecord
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val container: ActivityDependencies,
) : ViewModel() {
    private val _state = MutableStateFlow(ActivityUiState())
    val state: StateFlow<ActivityUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.runGate.state.collect { run ->
                _state.update { it.copy(busy = run.busy) }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val records = container.recorder.all()
            val conversations = container.conversations.all()
            val latest = conversations.maxByOrNull { it.updatedAt }
            val goal = latest?.messages?.lastOrNull { it.role == "user" }?.text.orEmpty()
            val week = weekRecords(records)
            _state.update {
                it.copy(
                    records = records,
                    lastGoal = goal,
                    lastSteps = lastSteps(records),
                    weekActions = week.size,
                    weekDone = week.count { record -> record.executed },
                    weekApps = week.count { record -> record.action.kind == "ui.launch" },
                    ready = AccessibilityState.isEnabled(container.context),
                )
            }
        }
    }

    fun shareText(): String {
        val current = _state.value
        return "HOPA \u00b7 Week in review\n" +
            "${current.weekDone} done\n" +
            "${current.weekActions} actions \u00b7 ${current.weekApps} apps\n" +
            "Last 7 days"
    }

    private fun lastSteps(records: List<RunRecord>): List<String> {
        val last = records.lastOrNull() ?: return emptyList()
        return records
            .filter { it.runId == last.runId }
            .map { it.action.kind }
            .distinct()
            .takeLast(3)
    }

    private fun weekRecords(records: List<RunRecord>): List<RunRecord> {
        val cutoff = Instant.now().minus(7, ChronoUnit.DAYS)
        return records.filter { record ->
            val timestamp = record.timestamp ?: return@filter true
            try {
                !Instant.parse(timestamp).isBefore(cutoff)
            } catch (error: Exception) {
                true
            }
        }
    }
}
