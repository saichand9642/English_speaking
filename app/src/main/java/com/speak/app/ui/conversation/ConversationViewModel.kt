package com.speak.app.ui.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.speak.app.audio.TtsEngine
import com.speak.app.audio.TurnRecorder
import com.speak.app.data.content.PracticeContent
import com.speak.app.data.content.Topic
import com.speak.app.data.repo.PracticeRepository
import com.speak.app.di.AppContainer
import com.speak.app.domain.correction.DiffAligner
import com.speak.app.domain.fluency.FluencyAnalyzer
import com.speak.app.domain.fluency.FluencyMetrics
import com.speak.app.domain.model.Correction
import com.speak.app.domain.model.DiffSpan
import com.speak.app.domain.model.PronunciationNote
import com.speak.app.domain.model.Utterance
import com.speak.app.domain.pronunciation.PronunciationAnalyzer
import com.speak.app.domain.pronunciation.RhythmAnalyzer
import com.speak.app.domain.pronunciation.RhythmMetrics
import com.speak.app.domain.tutor.TutorEngine
import com.speak.app.domain.tutor.TutorEvent
import com.speak.app.domain.tutor.TutorExchange
import com.speak.app.domain.tutor.TutorRequest
import com.speak.app.stt.WhisperTranscriber
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One completed exchange, as shown on screen. */
data class ConversationTurn(
    val transcript: String,
    val diff: List<DiffSpan>,
    val corrections: List<Correction>,
    val pronunciation: List<PronunciationNote>,
    val reply: String,
    val metrics: FluencyMetrics,
    val rhythm: RhythmMetrics?
) {
    val wasCorrect: Boolean get() = corrections.isEmpty()
}

enum class ConversationPhase {
    /** Nothing happening; the microphone is the only thing to press. */
    IDLE,

    /** Measuring the room's noise level. */
    CALIBRATING,

    /** Waiting for the first word. */
    LISTENING,

    /** Speech being captured. */
    HEARING,

    /** Running whisper. */
    TRANSCRIBING,

    /** Waiting for the tutor's first words. */
    THINKING,

    /** Tutor is talking. */
    REPLYING
}

data class ConversationUiState(
    val phase: ConversationPhase = ConversationPhase.IDLE,
    val level: Float = 0f,
    val topic: Topic = PracticeContent.topics.first(),
    val turns: List<ConversationTurn> = emptyList(),
    /** Reply text as it streams in, before the turn is complete. */
    val streamingReply: String = "",
    val loading: Boolean = true,
    val loadingMessage: String = "Getting ready…",
    val fatalError: String? = null,
    val transientError: String? = null,
    val tutorAvailable: Boolean = false,
    val usingOnline: Boolean = false,
    val geminiModel: String = "gemini-2.5-flash",
    val ttsStatus: TtsEngine.Status = TtsEngine.Status.IDLE
) {
    val isBusy: Boolean
        get() = phase != ConversationPhase.IDLE
}

/**
 * Drives the core loop: listen, transcribe, correct, speak.
 *
 * The ordering inside [runTurn] is what makes the app feel responsive despite a
 * CPU-only model. The tutor's reply is spoken sentence by sentence as it is
 * generated, so speech begins while the corrections are still being produced, and
 * the correction card appears on screen at the end without the learner having sat
 * in silence waiting for it.
 */
class ConversationViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(ConversationUiState())
    val state: StateFlow<ConversationUiState> = _state.asStateFlow()

    private val repository: PracticeRepository = container.repository
    private var sessionId: Long = 0L
    private var turnJob: Job? = null
    private val history = mutableListOf<TutorExchange>()

    init {
        viewModelScope.launch {
            container.settings.settings.collect { settings ->
                _state.update {
                    it.copy(
                        topic = PracticeContent.topicForDay(
                            PracticeRepository.today() + settings.topicIndex
                        ),
                        // Online is only ever used when the learner asked for it,
                        // a key exists, and the phone is actually connected.
                        usingOnline = settings.preferOnline &&
                            container.secureKeys.hasGeminiKey &&
                            container.hasInternet(),
                        geminiModel = settings.geminiModel
                    )
                }
            }
        }
        viewModelScope.launch { prepare() }
        viewModelScope.launch {
            container.ttsEngine.status.collect { status ->
                _state.update { it.copy(ttsStatus = status) }
            }
        }
    }

    private suspend fun prepare() {
        _state.update { it.copy(loading = true, loadingMessage = "Loading the speech model…") }

        container.ensureTranscriberReady().onFailure { error ->
            _state.update {
                it.copy(loading = false, fatalError = error.message ?: "Speech recognition is unavailable.")
            }
            return
        }

        _state.update { it.copy(loadingMessage = "Starting the voice…") }
        container.ttsEngine.start()

        // The tutor model is optional at this point: the learner may not have
        // downloaded it yet, and they should still be able to record and see a
        // transcript rather than being blocked behind a 769 MB download.
        _state.update { it.copy(loadingMessage = "Loading the tutor…") }
        val tutorReady = container.ensureTutorReady().isSuccess

        sessionId = repository.startSession("conversation", _state.value.topic.title)

        _state.update {
            it.copy(loading = false, tutorAvailable = tutorReady)
        }
    }

    fun onMicPressed() {
        val current = _state.value
        if (current.loading || current.fatalError != null) return

        if (current.isBusy) {
            stopTurn()
            return
        }
        turnJob = viewModelScope.launch { runTurn() }
    }

    private fun stopTurn() {
        turnJob?.cancel()
        turnJob = null
        container.ttsEngine.stop()
        engine()?.cancel()
        _state.update { it.copy(phase = ConversationPhase.IDLE, level = 0f, streamingReply = "") }
    }

    private suspend fun runTurn() {
        _state.update { it.copy(phase = ConversationPhase.CALIBRATING, transientError = null, streamingReply = "") }

        var audio: FloatArray? = null
        var speechMs = 0

        container.newTurnRecorder().record().collect { event ->
            when (event) {
                TurnRecorder.Event.Calibrating ->
                    _state.update { it.copy(phase = ConversationPhase.CALIBRATING) }

                TurnRecorder.Event.Listening ->
                    _state.update { it.copy(phase = ConversationPhase.LISTENING) }

                is TurnRecorder.Event.Speaking ->
                    _state.update { it.copy(phase = ConversationPhase.HEARING, level = event.level) }

                is TurnRecorder.Event.Captured -> {
                    audio = event.audio
                    speechMs = event.speechDurationMs
                }

                TurnRecorder.Event.NoSpeech ->
                    _state.update {
                        it.copy(
                            phase = ConversationPhase.IDLE,
                            level = 0f,
                            transientError = "I didn't hear anything. Tap the microphone and speak."
                        )
                    }
            }
        }

        val captured = audio ?: return

        // ---- transcribe ----
        _state.update { it.copy(phase = ConversationPhase.TRANSCRIBING, level = 0f) }
        val utterance = runCatching { container.transcriber.transcribe(captured, speechMs) }
            .getOrElse {
                _state.update {
                    it.copy(
                        phase = ConversationPhase.IDLE,
                        transientError = "I couldn't make out that recording. Try again."
                    )
                }
                return
            }

        if (utterance.text.isBlank()) {
            _state.update {
                it.copy(
                    phase = ConversationPhase.IDLE,
                    transientError = "That came through as silence. Try speaking a little louder."
                )
            }
            return
        }

        // ---- ask the tutor ----
        val tutorEngine = engine()
        if (tutorEngine == null) {
            // No tutor model yet: still show what was said, so the app is useful
            // before the download has happened.
            recordTurnWithoutTutor(utterance, captured)
            return
        }

        _state.update { it.copy(phase = ConversationPhase.THINKING) }

        val request = TutorRequest(
            transcript = utterance.text,
            topic = _state.value.topic.title,
            history = history.toList()
        )

        var spokenAnything = false
        var completed = false

        tutorEngine.respond(request).collect { event ->
            when (event) {
                TutorEvent.Started -> Unit

                is TutorEvent.ReplyDelta ->
                    _state.update { it.copy(streamingReply = it.streamingReply + event.text) }

                is TutorEvent.Speakable -> {
                    // First audio out: this is the moment the wait ends.
                    if (!spokenAnything) {
                        spokenAnything = true
                        _state.update { it.copy(phase = ConversationPhase.REPLYING) }
                    }
                    container.ttsEngine.speak(event.sentence, flush = false)
                }

                is TutorEvent.Complete -> {
                    completed = true
                    finishTurn(event.feedback, utterance, captured)
                }

                is TutorEvent.Failed ->
                    _state.update {
                        it.copy(phase = ConversationPhase.IDLE, transientError = event.message)
                    }
            }
        }

        if (!completed) {
            _state.update { it.copy(phase = ConversationPhase.IDLE, streamingReply = "") }
        }
    }

    private suspend fun finishTurn(
        feedback: com.speak.app.domain.model.TutorFeedback,
        utterance: Utterance,
        audio: FloatArray
    ) {
        val pronunciation = PronunciationAnalyzer.fromConfidence(utterance.words)
        val rhythm = RhythmAnalyzer.analyze(audio, WhisperTranscriber.AUDIO_SAMPLE_RATE)
        val metrics = FluencyAnalyzer.analyze(utterance, feedback.corrections.size)

        val diff = feedback.correctedSentence
            ?.let { DiffAligner.align(feedback.transcript, it) }
            ?: emptyList()

        val turn = ConversationTurn(
            transcript = feedback.transcript,
            diff = diff,
            corrections = feedback.corrections,
            pronunciation = pronunciation,
            reply = feedback.spokenReply,
            metrics = metrics,
            rhythm = rhythm.takeIf { it.verdict != null }
        )

        history += TutorExchange(feedback.transcript, feedback.spokenReply)
        if (history.size > MAX_HISTORY) history.removeAt(0)

        runCatching {
            repository.recordTurn(
                sessionId = sessionId,
                transcript = feedback.transcript,
                correctedSentence = feedback.correctedSentence,
                tutorReply = feedback.spokenReply,
                metrics = metrics,
                corrections = feedback.corrections,
                pronunciation = pronunciation
            )
        }

        _state.update {
            it.copy(
                phase = ConversationPhase.IDLE,
                level = 0f,
                streamingReply = "",
                turns = listOf(turn) + it.turns
            )
        }
    }

    /** Used when the tutor model is absent: transcript and fluency only. */
    private suspend fun recordTurnWithoutTutor(utterance: Utterance, audio: FloatArray) {
        val metrics = FluencyAnalyzer.analyze(utterance, 0)
        val pronunciation = PronunciationAnalyzer.fromConfidence(utterance.words)
        val rhythm = RhythmAnalyzer.analyze(audio, WhisperTranscriber.AUDIO_SAMPLE_RATE)

        runCatching {
            repository.recordTurn(
                sessionId = sessionId,
                transcript = utterance.text,
                correctedSentence = null,
                tutorReply = null,
                metrics = metrics,
                corrections = emptyList(),
                pronunciation = pronunciation
            )
        }

        _state.update {
            it.copy(
                phase = ConversationPhase.IDLE,
                level = 0f,
                turns = listOf(
                    ConversationTurn(
                        transcript = utterance.text,
                        diff = emptyList(),
                        corrections = emptyList(),
                        pronunciation = pronunciation,
                        reply = "",
                        metrics = metrics,
                        rhythm = rhythm.takeIf { r -> r.verdict != null }
                    )
                ) + it.turns,
                transientError = "Download the tutor model in Settings to get corrections."
            )
        }
    }

    /** Prefers Gemini only when explicitly enabled, keyed, and actually online. */
    private fun engine(): TutorEngine? {
        val current = _state.value
        if (current.usingOnline) {
            // Recreated per turn so a key change in Settings takes effect at once.
            return container.geminiEngine(current.geminiModel)
        }
        return container.localTutorEngine()
    }

    fun speakSlowly(word: String) {
        container.ttsEngine.speak(word, flush = true, slow = true)
    }

    fun replayReply(text: String) {
        container.ttsEngine.speak(text, flush = true)
    }

    fun nextTopic() {
        _state.update { current ->
            val index = PracticeContent.topics.indexOf(current.topic)
            current.copy(
                topic = PracticeContent.topics[(index + 1).mod(PracticeContent.topics.size)]
            )
        }
    }

    fun dismissTransientError() {
        _state.update { it.copy(transientError = null) }
    }

    override fun onCleared() {
        turnJob?.cancel()
        viewModelScope.launch { runCatching { repository.endSession(sessionId) } }
        container.ttsEngine.stop()
        super.onCleared()
    }

    companion object {
        private const val MAX_HISTORY = 4

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ConversationViewModel(container) }
        }
    }
}
