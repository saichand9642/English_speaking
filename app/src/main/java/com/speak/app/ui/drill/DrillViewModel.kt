package com.speak.app.ui.drill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.speak.app.audio.TurnRecorder
import com.speak.app.data.db.DrillCardEntity
import com.speak.app.di.AppContainer
import com.speak.app.domain.model.MistakeCategory
import com.speak.app.domain.srs.DrillGrade
import com.speak.app.domain.srs.DrillGrader
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DrillUiState(
    val loading: Boolean = true,
    val queue: List<DrillCardEntity> = emptyList(),
    val position: Int = 0,
    val recording: Boolean = false,
    val level: Float = 0f,
    val statusLabel: String = "Getting ready…",
    val heardText: String? = null,
    val lastResult: DrillGrader.Result? = null,
    val revealed: Boolean = false,
    val completedCount: Int = 0,
    val error: String? = null
) {
    val current: DrillCardEntity? get() = queue.getOrNull(position)
    val isFinished: Boolean get() = !loading && current == null
    val categoryLabel: String
        get() = current?.let { MistakeCategory.from(it.category).label } ?: ""
}

/**
 * Re-tests the mistakes this speaker actually made, on an SM-2 schedule.
 *
 * The queue is whatever is due today, ordered by how often the error has
 * recurred, so the habits that keep coming back are the ones drilled first.
 */
class DrillViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(DrillUiState())
    val state: StateFlow<DrillUiState> = _state.asStateFlow()

    private var sessionId: Long = 0L
    private var turnJob: Job? = null

    init {
        viewModelScope.launch {
            container.ensureTranscriberReady().onFailure { error ->
                _state.update {
                    it.copy(loading = false, statusLabel = "Unavailable", error = error.message)
                }
                return@launch
            }
            container.ttsEngine.start()
            sessionId = container.repository.startSession("drill", null)
            val due = container.repository.dueDrills()
            _state.update {
                it.copy(
                    loading = false,
                    queue = due,
                    statusLabel = if (due.isEmpty()) "Nothing due" else "Tap and say it correctly"
                )
            }
        }
    }

    fun onMicPressed() {
        val state = _state.value
        if (state.loading || state.current == null) return
        if (state.recording) {
            turnJob?.cancel()
            turnJob = null
            _state.update { it.copy(recording = false, level = 0f, statusLabel = "Tap and say it correctly") }
            return
        }
        turnJob = viewModelScope.launch { attempt() }
    }

    private suspend fun attempt() {
        val card = _state.value.current ?: return
        _state.update {
            it.copy(
                recording = true,
                error = null,
                heardText = null,
                lastResult = null,
                statusLabel = "Listening…"
            )
        }

        var audio: FloatArray? = null
        var speechMs = 0

        container.newTurnRecorder().record().collect { event ->
            when (event) {
                TurnRecorder.Event.Calibrating ->
                    _state.update { it.copy(statusLabel = "Listening to the room…") }
                TurnRecorder.Event.Listening ->
                    _state.update { it.copy(statusLabel = "Say the corrected sentence") }
                is TurnRecorder.Event.Speaking ->
                    _state.update { it.copy(level = event.level, statusLabel = "I can hear you") }
                is TurnRecorder.Event.Captured -> {
                    audio = event.audio
                    speechMs = event.speechDurationMs
                }
                TurnRecorder.Event.NoSpeech ->
                    _state.update {
                        it.copy(
                            recording = false,
                            level = 0f,
                            statusLabel = "Tap and say it correctly",
                            error = "I didn't hear anything that time."
                        )
                    }
            }
        }

        val captured = audio ?: return
        _state.update { it.copy(level = 0f, statusLabel = "Checking…") }

        val utterance = runCatching { container.transcriber.transcribe(captured, speechMs) }
            .getOrElse {
                _state.update {
                    it.copy(
                        recording = false,
                        statusLabel = "Tap and say it correctly",
                        error = "I couldn't check that. Try again."
                    )
                }
                return
            }

        val result = DrillGrader.grade(
            spoken = utterance.text,
            expectedFix = card.fixed,
            originalMistake = card.wrong
        )

        _state.update {
            it.copy(
                recording = false,
                heardText = utterance.text,
                lastResult = result,
                revealed = true,
                statusLabel = if (result.grade.isPass) "Correct" else "Not quite"
            )
        }
    }

    /** Commits the grade and moves on. Lets the learner override the automatic call. */
    fun commit(grade: DrillGrade) {
        val card = _state.value.current ?: return
        viewModelScope.launch {
            runCatching { container.repository.gradeDrill(card, grade) }
            _state.update {
                it.copy(
                    position = it.position + 1,
                    heardText = null,
                    lastResult = null,
                    revealed = false,
                    completedCount = it.completedCount + 1,
                    statusLabel = "Tap and say it correctly"
                )
            }
        }
    }

    fun acceptAutomaticGrade() {
        commit(_state.value.lastResult?.grade ?: DrillGrade.HESITANT)
    }

    fun skip() {
        _state.update {
            it.copy(
                position = it.position + 1,
                heardText = null,
                lastResult = null,
                revealed = false,
                statusLabel = "Tap and say it correctly"
            )
        }
    }

    fun reveal() {
        _state.update { it.copy(revealed = true) }
    }

    fun hearCorrect() {
        val card = _state.value.current ?: return
        container.ttsEngine.speak(card.fixed, flush = true)
    }

    override fun onCleared() {
        turnJob?.cancel()
        viewModelScope.launch { runCatching { container.repository.endSession(sessionId) } }
        container.ttsEngine.stop()
        super.onCleared()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { DrillViewModel(container) }
        }
    }
}
