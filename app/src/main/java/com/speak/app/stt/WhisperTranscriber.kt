package com.speak.app.stt

import com.speak.app.domain.model.SpokenWord
import com.speak.app.domain.model.Utterance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Offline speech-to-text. This is the guaranteed path: the model ships inside the
 * APK, so transcription works on first launch, in aeroplane mode, with no
 * download and no Google service involved.
 */
class WhisperTranscriber(
    private val threads: Int = defaultThreads()
) {
    private val bridge = WhisperBridge()

    // whisper_full mutates the context, so one transcription at a time.
    private val mutex = Mutex()

    val isReady: Boolean get() = bridge.isLoaded

    suspend fun load(modelPath: String): Boolean = withContext(Dispatchers.IO) {
        WhisperBridge.ensureLibraryLoaded()
        mutex.withLock { bridge.load(modelPath) }
    }

    /**
     * Transcribes one utterance of 16 kHz mono float PCM.
     *
     * @param durationMsOverride speech duration measured by the recorder, which
     *   excludes trailing silence and so gives a truer words-per-minute figure
     *   than the raw buffer length.
     */
    suspend fun transcribe(samples: FloatArray, durationMsOverride: Int? = null): Utterance =
        withContext(Dispatchers.Default) {
            if (samples.isEmpty()) return@withContext Utterance.EMPTY
            val json = mutex.withLock { bridge.transcribeToJson(samples, threads) }
            val parsed = WhisperOutputParser.parse(json)
            val durationMs = durationMsOverride?.toLong()
                ?: (samples.size * 1000L / AUDIO_SAMPLE_RATE)
            parsed.copy(durationMs = durationMs)
        }

    fun systemInfo(): String = runCatching {
        WhisperBridge.ensureLibraryLoaded()
        bridge.systemInfo()
    }.getOrElse { "unavailable" }

    fun release() = bridge.release()

    companion object {
        const val AUDIO_SAMPLE_RATE = 16_000

        /**
         * Whisper scales well up to about four threads and then stops improving,
         * while starving the UI thread. Leaving cores free keeps the app
         * responsive during transcription.
         */
        fun defaultThreads(): Int =
            Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
    }
}

/**
 * Parses the JSON produced by `whisper_jni.cpp` into words with confidences.
 *
 * Whisper works in sub-word tokens, so tokens are stitched back into words on
 * their leading-space boundaries and each word's confidence is the mean of its
 * tokens' probabilities. That average is the number the pronunciation feature
 * relies on, which is why this is a plain, separately testable object rather than
 * being buried in the transcriber.
 */
internal object WhisperOutputParser {

    fun parse(json: String): Utterance {
        if (json.isBlank()) return Utterance.EMPTY
        val root = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(json)
                as? kotlinx.serialization.json.JsonObject
        }.getOrNull() ?: return Utterance.EMPTY

        val segments = root["segments"] as? kotlinx.serialization.json.JsonArray
            ?: return Utterance.EMPTY

        val words = mutableListOf<SpokenWord>()
        var pendingText = StringBuilder()
        var pendingProbSum = 0.0
        var pendingProbCount = 0
        var pendingStart = 0L
        var pendingEnd = 0L

        fun flush() {
            val text = pendingText.toString().trim()
            if (text.isNotEmpty() && pendingProbCount > 0) {
                words += SpokenWord(
                    text = text,
                    confidence = (pendingProbSum / pendingProbCount).toFloat(),
                    startMs = pendingStart,
                    endMs = pendingEnd
                )
            }
            pendingText = StringBuilder()
            pendingProbSum = 0.0
            pendingProbCount = 0
        }

        for (segmentElement in segments) {
            val segment = segmentElement as? kotlinx.serialization.json.JsonObject ?: continue
            val tokens = segment["tokens"] as? kotlinx.serialization.json.JsonArray ?: continue
            for (tokenElement in tokens) {
                val token = tokenElement as? kotlinx.serialization.json.JsonObject ?: continue
                val text = token.stringOrNull("text") ?: continue
                if (text.isBlank() && !text.startsWith(" ")) continue
                val probability = token.floatOrNull("p") ?: 0f
                // Whisper marks the start of a new word with a leading space.
                if (text.startsWith(" ") && pendingText.isNotEmpty()) flush()
                if (pendingText.isEmpty()) {
                    pendingStart = token.longOrNull("t0") ?: 0L
                }
                pendingEnd = token.longOrNull("t1") ?: pendingEnd
                pendingText.append(text)
                pendingProbSum += probability
                pendingProbCount++
            }
        }
        flush()

        val text = words.joinToString(" ") { it.text }.trim()
        return Utterance(
            text = text,
            words = words,
            // Timestamps are in centiseconds; the caller normally overrides this.
            durationMs = words.lastOrNull()?.endMs?.times(10) ?: 0L
        )
    }

    private fun kotlinx.serialization.json.JsonObject.stringOrNull(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content

    private fun kotlinx.serialization.json.JsonObject.floatOrNull(key: String): Float? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toFloatOrNull()

    private fun kotlinx.serialization.json.JsonObject.longOrNull(key: String): Long? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull()
}
