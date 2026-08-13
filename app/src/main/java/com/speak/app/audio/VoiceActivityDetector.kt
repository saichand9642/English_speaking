package com.speak.app.audio

import kotlin.math.sqrt

/**
 * Decides when the speaker has finished, so a turn ends on its own rather than
 * needing a second tap.
 *
 * The design constraint that matters is the room: this has to work while walking
 * outdoors and in a quiet room, without being configured. So instead of a fixed
 * energy threshold it learns the noise floor from the opening moments of the
 * recording and requires speech to stand a fixed margin above whatever that
 * floor turns out to be.
 *
 * A zero-crossing-rate check rides alongside it. Unvoiced consonants like "s"
 * and "f" carry little energy but cross zero often, so energy alone clips them
 * off the end of words -- which is exactly where a learner's final consonants
 * need to be heard.
 *
 * This class is deliberately free of Android dependencies so the whole decision
 * path is unit-testable against synthetic audio.
 */
class VoiceActivityDetector(
    private val sampleRate: Int = 16_000,
    /** Speech must exceed the learned noise floor by this much, in dB. */
    private val speechMarginDb: Double = 9.0,
    /** Silence this long after speech ends the turn. */
    private val trailingSilenceMs: Int = 900,
    /** Ignore bursts shorter than this; they are clicks, not words. */
    private val minSpeechMs: Int = 250,
    /** Give up waiting for speech to start after this long. */
    private val noSpeechTimeoutMs: Int = 8_000,
    /** Hard ceiling on one turn. */
    private val maxTurnMs: Int = 60_000
) {
    enum class State {
        /** Learning the noise floor. */
        CALIBRATING,

        /** Calibrated, waiting for the speaker to begin. */
        WAITING,

        /** Speech in progress. */
        SPEAKING,

        /** Speech ended; the turn is complete. */
        FINISHED,

        /** Nobody spoke. */
        TIMED_OUT
    }

    /** Frames used to learn the noise floor before any decision is made. */
    private val calibrationFrames = 12

    private val noiseFrames = mutableListOf<Double>()
    private var noiseFloorDb = -60.0

    private var elapsedMs = 0
    private var speechMs = 0
    private var silenceMsSinceSpeech = 0
    private var sawSpeech = false

    var state: State = State.CALIBRATING
        private set

    /** Most recent frame level, for the microphone animation. */
    var lastLevelDb: Double = -100.0
        private set

    /** 0f..1f, suitable for driving a level meter. */
    val normalisedLevel: Float
        get() = ((lastLevelDb - noiseFloorDb) / 30.0).coerceIn(0.0, 1.0).toFloat()

    /**
     * Feeds one frame of mono PCM. Frames are expected to be a consistent length;
     * 20 ms (320 samples at 16 kHz) works well.
     *
     * @return the state after this frame.
     */
    fun accept(frame: FloatArray): State {
        if (state == State.FINISHED || state == State.TIMED_OUT) return state
        if (frame.isEmpty()) return state

        val frameMs = frame.size * 1000 / sampleRate
        elapsedMs += frameMs

        val db = levelDb(frame)
        lastLevelDb = db

        if (state == State.CALIBRATING) {
            noiseFrames += db
            if (noiseFrames.size >= calibrationFrames) {
                // Median, not mean: a cough or a door during calibration should not
                // drag the floor up and deafen the detector for the whole turn.
                noiseFloorDb = noiseFrames.sorted()[noiseFrames.size / 2]
                state = State.WAITING
            }
            return state
        }

        val isSpeech = db > noiseFloorDb + speechMarginDb || isUnvoicedSpeech(frame, db)

        if (isSpeech) {
            speechMs += frameMs
            silenceMsSinceSpeech = 0
            if (speechMs >= minSpeechMs) {
                sawSpeech = true
                state = State.SPEAKING
            }
        } else {
            if (sawSpeech) {
                silenceMsSinceSpeech += frameMs
                if (silenceMsSinceSpeech >= trailingSilenceMs) {
                    state = State.FINISHED
                    return state
                }
            } else {
                // Decay the counter so scattered noise never accumulates into "speech".
                speechMs = (speechMs - frameMs).coerceAtLeast(0)
            }
        }

        if (!sawSpeech && elapsedMs >= noSpeechTimeoutMs) {
            state = State.TIMED_OUT
        } else if (elapsedMs >= maxTurnMs) {
            state = if (sawSpeech) State.FINISHED else State.TIMED_OUT
        }
        return state
    }

    /**
     * Trailing silence is padding, not speech, so the duration used for
     * words-per-minute excludes it.
     */
    fun speechDurationMs(): Int = (elapsedMs - silenceMsSinceSpeech).coerceAtLeast(0)

    fun reset() {
        noiseFrames.clear()
        noiseFloorDb = -60.0
        elapsedMs = 0
        speechMs = 0
        silenceMsSinceSpeech = 0
        sawSpeech = false
        lastLevelDb = -100.0
        state = State.CALIBRATING
    }

    /**
     * True for quiet but busy frames: fricatives such as "s", "sh" and "f" sit
     * only a little above the noise floor but cross zero far more often than
     * room noise does.
     */
    private fun isUnvoicedSpeech(frame: FloatArray, db: Double): Boolean {
        if (db < noiseFloorDb + 4.0) return false
        var crossings = 0
        for (index in 1 until frame.size) {
            if ((frame[index - 1] < 0f) != (frame[index] < 0f)) crossings++
        }
        val rate = crossings.toDouble() / frame.size
        return rate > 0.25
    }

    private fun levelDb(frame: FloatArray): Double {
        var sum = 0.0
        for (sample in frame) sum += sample.toDouble() * sample.toDouble()
        val rms = sqrt(sum / frame.size)
        return 20.0 * kotlin.math.log10(rms + 1e-10)
    }
}
