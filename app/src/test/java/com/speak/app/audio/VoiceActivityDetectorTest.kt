package com.speak.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * The audio pipeline's decision layer, tested against synthetic signals.
 *
 * These are the cases that decide whether a turn ends at the right moment: too
 * eager and it cuts the learner off mid-sentence, too slow and they sit waiting.
 * Both failures are worse than a wrong correction, because they stop the
 * conversation happening at all.
 */
class VoiceActivityDetectorTest {

    private val sampleRate = 16_000
    private val frameSamples = 320 // 20 ms

    private fun silence(amplitude: Float = 0.0005f, seed: Int = 1): FloatArray {
        val random = Random(seed)
        return FloatArray(frameSamples) { (random.nextFloat() - 0.5f) * 2f * amplitude }
    }

    /** A voiced tone: high energy, low zero-crossing rate. */
    private fun tone(amplitude: Float = 0.3f, frequency: Double = 140.0): FloatArray =
        FloatArray(frameSamples) { index ->
            (amplitude * sin(2 * PI * frequency * index / sampleRate)).toFloat()
        }

    /** A fricative such as "s": low energy but crossing zero constantly. */
    private fun fricative(amplitude: Float = 0.01f, seed: Int = 7): FloatArray {
        val random = Random(seed)
        return FloatArray(frameSamples) { index ->
            val sign = if (index % 2 == 0) 1f else -1f
            sign * amplitude * (0.5f + random.nextFloat() * 0.5f)
        }
    }

    private fun feed(
        detector: VoiceActivityDetector,
        frame: () -> FloatArray,
        frames: Int
    ): VoiceActivityDetector.State {
        var state = detector.state
        repeat(frames) { state = detector.accept(frame()) }
        return state
    }

    private fun calibrate(detector: VoiceActivityDetector, frames: Int = 14) {
        repeat(frames) { detector.accept(silence()) }
    }

    @Test
    fun `starts by calibrating then waits`() {
        val detector = VoiceActivityDetector(sampleRate = sampleRate)
        assertEquals(VoiceActivityDetector.State.CALIBRATING, detector.state)
        calibrate(detector)
        assertEquals(VoiceActivityDetector.State.WAITING, detector.state)
    }

    @Test
    fun `detects speech well above the noise floor`() {
        val detector = VoiceActivityDetector(sampleRate = sampleRate)
        calibrate(detector)
        val state = feed(detector, { tone() }, 20)
        assertEquals(VoiceActivityDetector.State.SPEAKING, state)
    }

    @Test
    fun `ends the turn after trailing silence`() {
        val detector = VoiceActivityDetector(
            sampleRate = sampleRate,
            trailingSilenceMs = 400
        )
        calibrate(detector)
        feed(detector, { tone() }, 30)
        assertEquals(VoiceActivityDetector.State.SPEAKING, detector.state)

        // 400 ms of silence at 20 ms a frame is 20 frames.
        val state = feed(detector, { silence() }, 25)
        assertEquals(VoiceActivityDetector.State.FINISHED, state)
    }

    @Test
    fun `does not end the turn on a short pause between words`() {
        val detector = VoiceActivityDetector(
            sampleRate = sampleRate,
            trailingSilenceMs = 900
        )
        calibrate(detector)
        feed(detector, { tone() }, 20)
        // A 300 ms gap, as between clauses, must not finish the turn.
        feed(detector, { silence() }, 15)
        assertTrue(detector.state != VoiceActivityDetector.State.FINISHED)
        val state = feed(detector, { tone() }, 10)
        assertEquals(VoiceActivityDetector.State.SPEAKING, state)
    }

    @Test
    fun `unvoiced fricatives count as speech`() {
        // Energy alone would clip a trailing "s" off the end of a word, which is
        // exactly where a learner's final consonants need to be heard.
        val detector = VoiceActivityDetector(
            sampleRate = sampleRate,
            trailingSilenceMs = 300
        )
        calibrate(detector)
        feed(detector, { tone() }, 20)
        val state = feed(detector, { fricative() }, 12)
        assertEquals(VoiceActivityDetector.State.SPEAKING, state)
    }

    @Test
    fun `times out when nobody speaks`() {
        val detector = VoiceActivityDetector(
            sampleRate = sampleRate,
            noSpeechTimeoutMs = 600
        )
        calibrate(detector)
        val state = feed(detector, { silence() }, 40)
        assertEquals(VoiceActivityDetector.State.TIMED_OUT, state)
    }

    @Test
    fun `ignores a brief click`() {
        val detector = VoiceActivityDetector(
            sampleRate = sampleRate,
            minSpeechMs = 250,
            noSpeechTimeoutMs = 4_000
        )
        calibrate(detector)
        // Two frames is 40 ms: a door or a tap, not a word.
        feed(detector, { tone() }, 2)
        feed(detector, { silence() }, 10)
        assertEquals(VoiceActivityDetector.State.WAITING, detector.state)
    }

    @Test
    fun `adapts to a loud room`() {
        // Calibrated against loud background noise, that same noise must not then
        // register as speech. This is what makes it work outdoors without tuning.
        val detector = VoiceActivityDetector(sampleRate = sampleRate)
        repeat(14) { detector.accept(silence(amplitude = 0.05f, seed = 3)) }
        assertEquals(VoiceActivityDetector.State.WAITING, detector.state)
        feed(detector, { silence(amplitude = 0.05f, seed = 4) }, 10)
        assertTrue(detector.state == VoiceActivityDetector.State.WAITING)
    }

    @Test
    fun `a single loud burst during calibration does not deafen the detector`() {
        // The noise floor is a median, so one cough while calibrating cannot drag
        // it up and make real speech undetectable for the rest of the turn.
        val detector = VoiceActivityDetector(sampleRate = sampleRate)
        detector.accept(tone(amplitude = 0.9f))
        repeat(13) { detector.accept(silence()) }
        assertEquals(VoiceActivityDetector.State.WAITING, detector.state)
        val state = feed(detector, { tone() }, 20)
        assertEquals(VoiceActivityDetector.State.SPEAKING, state)
    }

    @Test
    fun `speech duration excludes trailing silence`() {
        val detector = VoiceActivityDetector(
            sampleRate = sampleRate,
            trailingSilenceMs = 400
        )
        calibrate(detector)
        feed(detector, { tone() }, 50)   // 1000 ms of speech
        val beforeSilence = detector.speechDurationMs()
        feed(detector, { silence() }, 25) // finishes the turn
        val afterSilence = detector.speechDurationMs()

        // Words per minute would be wrong if padding counted as speaking time.
        assertTrue(afterSilence <= beforeSilence + 40)
        assertTrue(afterSilence > 0)
    }

    @Test
    fun `caps a runaway turn`() {
        val detector = VoiceActivityDetector(
            sampleRate = sampleRate,
            maxTurnMs = 1_000,
            trailingSilenceMs = 5_000
        )
        calibrate(detector)
        val state = feed(detector, { tone() }, 100)
        assertEquals(VoiceActivityDetector.State.FINISHED, state)
    }

    @Test
    fun `reset returns it to calibrating`() {
        val detector = VoiceActivityDetector(sampleRate = sampleRate)
        calibrate(detector)
        feed(detector, { tone() }, 20)
        detector.reset()
        assertEquals(VoiceActivityDetector.State.CALIBRATING, detector.state)
        assertEquals(0, detector.speechDurationMs())
    }

    @Test
    fun `level is normalised into zero to one`() {
        val detector = VoiceActivityDetector(sampleRate = sampleRate)
        calibrate(detector)
        detector.accept(tone(amplitude = 0.8f))
        assertTrue(detector.normalisedLevel in 0f..1f)
        detector.accept(silence())
        assertTrue(detector.normalisedLevel in 0f..1f)
    }

    @Test
    fun `an empty frame is ignored`() {
        val detector = VoiceActivityDetector(sampleRate = sampleRate)
        val state = detector.accept(FloatArray(0))
        assertEquals(VoiceActivityDetector.State.CALIBRATING, state)
    }
}
