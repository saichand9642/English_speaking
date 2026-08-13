package com.speak.app.ui.readaloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.speak.app.audio.TurnRecorder
import com.speak.app.data.content.PracticeContent
import com.speak.app.data.content.ReadAloudSentence
import com.speak.app.data.repo.PracticeRepository
import com.speak.app.di.AppContainer
import com.speak.app.domain.fluency.FluencyAnalyzer
import com.speak.app.domain.model.PronunciationNote
import com.speak.app.domain.pronunciation.PronunciationAnalyzer
import com.speak.app.domain.pronunciation.RhythmAnalyzer
import com.speak.app.domain.pronunciation.RhythmMetrics
import com.speak.app.domain.pronunciation.WordScore
import com.speak.app.stt.WhisperTranscriber
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

data class ReadAloudUiState(
    val sentence: ReadAloudSentence = PracticeContent.readAloudSentences.first(),
    val recording: Boolean = false,
    val level: Float = 0f,
    val loading: Boolean = true,
    val statusLabel: String = "Getting ready…",
    val scores: List<WordScore> = emptyList(),
    val notes: List<PronunciationNote> = emptyList(),
    val accuracyPercent: Int = 0,
    val heardText: String = "",
    val rhythm: RhythmMetrics? = null,
    val error: String? = null
)

class ReadAloudViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(ReadAloudUiState())
    val state: StateFlow<ReadAloudUiState> = _state.asStateFlow()

    private var sessionId: Long = 0L
    private var turnJob: Job? = null
    private var index: Int = Random.nextInt(PracticeContent.readAloudSentences.size)

    init {
        _state.update { it.copy(sentence = PracticeContent.sentenceAt(index)) }
        viewModelScope.launch {
            container.ensureTranscriberReady()
                .onSuccess {
                    sessionId = container.repository.startSession("read_aloud", null)
                    _state.update { it.copy(loading = false, statusLabel = "Tap and read it aloud") }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(loading = false, statusLabel = "Unavailable", error = error.message)
                    }
                }
            container.ttsEngine.start()
        }
    }

    fun onMicPressed() {
        if (_state.value.loading) return
        if (_state.value.recording) {
            turnJob?.cancel()
            turnJob = null
            _state.update { it.copy(recording = false, level = 0f, statusLabel = "Tap and read it aloud") }
            return
        }
        turnJob = viewModelScope.launch { runAttempt() }
    }

    private suspend fun runAttempt() {
        _state.update {
            it.copy(
                recording = true,
                error = null,
                scores = emptyList(),
                notes = emptyList(),
                heardText = "",
                rhythm = null,
                statusLabel = "Listening to the room…"
            )
        }

        var audio: FloatArray? = null
        var speechMs = 0

        container.newTurnRecorder().record().collect { event ->
            when (event) {
                TurnRecorder.Event.Calibrating ->
                    _state.update { it.copy(statusLabel = "Listening to the room…") }
                TurnRecorder.Event.Listening ->
                    _state.update { it.copy(statusLabel = "Read the sentence") }
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
                            statusLabel = "Tap and read it aloud",
                            error = "I didn't hear anything that time."
                        )
                    }
            }
        }

        val captured = audio ?: return
        _state.update { it.copy(level = 0f, statusLabel = "Scoring…") }

        val target = _state.value.sentence.text
        val utterance = runCatching { container.transcriber.transcribe(captured, speechMs) }
            .getOrElse {
                _state.update {
                    it.copy(
                        recording = false,
                        statusLabel = "Tap and read it aloud",
                        error = "I couldn't score that recording. Try again."
                    )
                }
                return
            }

        val scores = PronunciationAnalyzer.scoreReadAloud(target, utterance.words)
        val notes = PronunciationAnalyzer.notesFor(scores)
        val rhythm = RhythmAnalyzer.analyze(captured, WhisperTranscriber.AUDIO_SAMPLE_RATE)

        // Read-aloud attempts are logged as turns so they feed the same progress
        // charts as conversation, but with no grammar mistakes attached: reading
        // someone else's sentence says nothing about the learner's own grammar.
        runCatching {
            container.repository.recordTurn(
                sessionId = sessionId,
                transcript = utterance.text,
                correctedSentence = target,
                tutorReply = null,
                metrics = FluencyAnalyzer.analyze(utterance, 0),
                corrections = emptyList(),
                pronunciation = notes
            )
        }

        _state.update {
            it.copy(
                recording = false,
                statusLabel = "Tap to try again",
                scores = scores,
                notes = notes,
                accuracyPercent = PronunciationAnalyzer.accuracyPercent(scores),
                heardText = utterance.text,
                rhythm = rhythm
            )
        }
    }

    fun nextSentence() {
        index = (index + 1).mod(PracticeContent.readAloudSentences.size)
        _state.update {
            it.copy(
                sentence = PracticeContent.sentenceAt(index),
                scores = emptyList(),
                notes = emptyList(),
                heardText = "",
                rhythm = null,
                accuracyPercent = 0,
                error = null,
                statusLabel = "Tap and read it aloud"
            )
        }
    }

    fun hearSentence() {
        container.ttsEngine.speak(_state.value.sentence.text, flush = true)
    }

    fun speakSlowly(word: String) {
        container.ttsEngine.speak(word, flush = true, slow = true)
    }

    override fun onCleared() {
        turnJob?.cancel()
        viewModelScope.launch { runCatching { container.repository.endSession(sessionId) } }
        container.ttsEngine.stop()
        super.onCleared()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReadAloudViewModel(container) }
        }
    }
}
