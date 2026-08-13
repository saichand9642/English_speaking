package com.speak.app.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * Speaks the tutor's replies using the device's own text-to-speech engine.
 *
 * Everything here is built around one requirement: it must work in aeroplane
 * mode. Android's TTS will happily fall back to a network voice, which sounds
 * better and then fails silently when offline, so this class explicitly seeks out
 * a voice that declares `isNetworkConnectionRequired == false` and reports
 * honestly when no such voice is installed.
 */
class TtsEngine(private val context: Context) {

    enum class Status {
        /** Not started yet. */
        IDLE,

        /** Engine is starting. */
        STARTING,

        /** An offline English voice is installed and selected. */
        READY_OFFLINE,

        /** Only a network voice is available; replies will fail offline. */
        READY_ONLINE_ONLY,

        /** No usable English voice at all. */
        NO_VOICE,

        /** The engine itself failed to start. */
        FAILED
    }

    private val _status = MutableStateFlow(Status.IDLE)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var tts: TextToSpeech? = null
    private val utteranceCounter = AtomicInteger(0)
    private var selectedVoice: Voice? = null

    /** Starts the engine and picks the best available voice. */
    suspend fun start(): Status = suspendCancellableCoroutine { continuation ->
        if (_status.value == Status.READY_OFFLINE || _status.value == Status.READY_ONLINE_ONLY) {
            continuation.resume(_status.value)
            return@suspendCancellableCoroutine
        }
        _status.value = Status.STARTING

        val engine = TextToSpeech(context) { result ->
            val status = if (result == TextToSpeech.SUCCESS) configureVoice() else Status.FAILED
            _status.value = status
            if (continuation.isActive) continuation.resume(status)
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { _isSpeaking.value = true }
            override fun onDone(utteranceId: String?) { _isSpeaking.value = false }
            @Deprecated("Required by the base class")
            override fun onError(utteranceId: String?) { _isSpeaking.value = false }
            override fun onError(utteranceId: String?, errorCode: Int) { _isSpeaking.value = false }
            override fun onStop(utteranceId: String?, interrupted: Boolean) { _isSpeaking.value = false }
        })
        tts = engine

        continuation.invokeOnCancellation { shutdown() }
    }

    /**
     * Prefers an installed, offline, English voice. Indian English is tried first
     * because a familiar accent is easier to model your own speech on, but any
     * offline English voice is far better than a network one.
     */
    private fun configureVoice(): Status {
        val engine = tts ?: return Status.FAILED
        val voices = runCatching { engine.voices }.getOrNull().orEmpty()
            .filter { it.locale.language == "en" }

        val installed = voices.filter { voice ->
            !voice.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
        }
        val offline = installed.filter { !it.isNetworkConnectionRequired }

        val preferredOrder = listOf("en_IN", "en_GB", "en_US")
        val chosen = preferredOrder.firstNotNullOfOrNull { tag ->
            offline.firstOrNull { it.locale.toString().equals(tag, ignoreCase = true) }
        }
            ?: offline.maxByOrNull { it.quality }
            ?: installed.maxByOrNull { it.quality }

        if (chosen == null) {
            // No voice objects at all: fall back to a plain locale check so an
            // engine with a minimal voice list still works.
            val localeResult = engine.setLanguage(Locale.UK)
            return if (localeResult == TextToSpeech.LANG_MISSING_DATA ||
                localeResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) Status.NO_VOICE else Status.READY_ONLINE_ONLY
        }

        selectedVoice = chosen
        engine.voice = chosen
        engine.setSpeechRate(NORMAL_RATE)
        engine.setPitch(1.0f)

        return if (chosen.isNetworkConnectionRequired) Status.READY_ONLINE_ONLY else Status.READY_OFFLINE
    }

    /** The name of the voice in use, for the settings screen. */
    fun voiceDescription(): String? = selectedVoice?.let { "${it.locale.displayName} (${it.name})" }

    /**
     * Speaks [text]. When [flush] is false the text is queued behind whatever is
     * already speaking, which is what lets the tutor's reply be spoken sentence by
     * sentence while the model is still generating the rest of it.
     */
    fun speak(text: String, flush: Boolean = false, slow: Boolean = false) {
        val engine = tts ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        engine.setSpeechRate(if (slow) SLOW_RATE else NORMAL_RATE)
        val id = "speak-${utteranceCounter.incrementAndGet()}"
        engine.speak(
            trimmed,
            if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
            null,
            id
        )
    }

    fun stop() {
        runCatching { tts?.stop() }
        _isSpeaking.value = false
    }

    fun shutdown() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        selectedVoice = null
        _isSpeaking.value = false
        _status.value = Status.IDLE
    }

    companion object {
        private const val NORMAL_RATE = 0.94f

        /** Used by the "hear it slowly" button on pronunciation cards. */
        private const val SLOW_RATE = 0.55f
    }
}
