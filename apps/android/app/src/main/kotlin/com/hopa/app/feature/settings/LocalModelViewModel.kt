package com.hopa.app.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopa.app.di.LocalModelHost
import com.hopa.feature.settings.LocalModelUiState
import com.hopa.inference.local.ImportOutcome
import com.hopa.inference.local.LocalModelCatalogue
import com.hopa.inference.local.LocalModelRecommendation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The on-device section: what to offer this phone, what is installed, and the
 * one import or delete the person asked for.
 */
@HiltViewModel
class LocalModelViewModel @Inject constructor(
    private val host: LocalModelHost,
) : ViewModel() {
    private val _state = MutableStateFlow(LocalModelUiState())
    val state: StateFlow<LocalModelUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val installed = host.localModels.installed()
            val recommended = LocalModelRecommendation().recommend(
                ramGb = host.ramGb,
                freeBytes = host.freeBytes,
            )
            _state.update {
                it.copy(
                    recommended = recommended,
                    installed = installed,
                    installedSpecs = LocalModelCatalogue.models.associateBy { model -> model.id },
                    totalBytes = installed.sumOf { model -> model.bytes },
                    ramGb = host.ramGb,
                    error = null,
                )
            }
        }
    }

    fun importRecommended(uri: Uri) {
        val spec = _state.value.recommended
        if (spec == null) {
            _state.update { it.copy(error = "This phone has no model that fits its free space.") }
            return
        }
        if (spec.bytes > host.freeBytes) {
            _state.update { it.copy(error = "Not enough free space for ${spec.name}.") }
            return
        }
        _state.update { it.copy(busy = true, error = null, notice = null) }
        viewModelScope.launch {
            val outcome = host.localModels.import(uri, spec)
            _state.update {
                it.copy(busy = false, notice = noticeFor(outcome), error = errorFor(outcome))
            }
            refresh()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            host.localModels.delete(id)
            _state.update { it.copy(notice = "Model removed.") }
            refresh()
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            host.localModels.cleanup(keep = emptySet())
            _state.update { it.copy(notice = "All models removed.") }
            refresh()
        }
    }

    private fun noticeFor(outcome: ImportOutcome): String? = when (outcome) {
        is ImportOutcome.Imported -> "Model ready."
        else -> null
    }

    private fun errorFor(outcome: ImportOutcome): String? = when (outcome) {
        is ImportOutcome.Imported -> null
        is ImportOutcome.HashMismatch -> "The file does not match this model; nothing was installed."
        ImportOutcome.TooLarge -> "The file is larger than this model should be."
        ImportOutcome.Empty -> "The file is empty."
    }
}
