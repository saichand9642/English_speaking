package com.speak.app.domain.pronunciation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * Rhythm is the one part of pronunciation feedback that can be measured exactly
 * rather than inferred, so it is tested against signals with a known answer.
 */
class RhythmAnalyzerTest {

    private val sampleRate = 16_000

    /**
     * Builds a signal of syllable-like energy bursts.
     *
     * @param durationsMs length of each burst, in order
     * @param gapMs silence between bursts
     */
    private fun syllables(
        durationsMs: List<Int>,
        gapMs: Int = 60,
        /** Per-gap overrides, used to build uneven spacing. */
        gapsMs: List<Int>? = null
    ): FloatArray {
        val samples = mutableListOf<Float>()
        fun appendSilence(ms: Int) {
            repeat(sampleRate * ms / 1000) { samples += 0.0f }
        }
        appendSilence(120)
        durationsMs.forEachIndexed { index, ms ->
            val count = sampleRate * ms / 1000
            repeat(count) { sampleIndex ->
                // A voiced burst with a smooth envelope, so each one produces a
                // single clear energy peak rather than a plateau.
                val phase = sampleIndex.toDouble() / count
                val envelope = sin(PI * phase)
                samples += (0.5 * envelope * sin(2 * PI * 150.0 * sampleIndex / sampleRate)).toFloat()
            }
            if (index != durationsMs.lastIndex) {
                appendSilence(gapsMs?.getOrNull(index) ?: gapMs)
            }
        }
        appendSilence(120)
        return samples.toFloatArray()
    }

    @Test
    fun `silence yields nothing`() {
        val metrics = RhythmAnalyzer.analyze(FloatArray(sampleRate), sampleRate)
        assertEquals(0.0, metrics.npvi, 0.001)
        assertNull(metrics.verdict)
    }

    @Test
    fun `empty input is safe`() {
        val metrics = RhythmAnalyzer.analyze(FloatArray(0), sampleRate)
        assertEquals(0, metrics.syllableCount)
        assertEquals(0.0, metrics.syllablesPerSecond, 0.001)
    }

    @Test
    fun `counts syllable nuclei`() {
        val metrics = RhythmAnalyzer.analyze(syllables(List(8) { 150 }), sampleRate)
        // Peak detection on real audio is approximate; being within a couple of
        // syllables is enough for the rhythm statistic to be meaningful.
        assertTrue(
            "expected about 8 syllables, found ${metrics.syllableCount}",
            metrics.syllableCount in 6..10
        )
    }

    @Test
    fun `evenly spaced syllables give a low nPVI`() {
        // Equal-length syllables are the syllable-timed pattern this app exists to
        // point out to Indian-English speakers.
        val metrics = RhythmAnalyzer.analyze(syllables(List(10) { 150 }), sampleRate)
        assertTrue("nPVI was ${metrics.npvi}", metrics.npvi < 25.0)
    }

    @Test
    fun `unevenly spaced syllables give a high nPVI`() {
        // The stress-timed pattern: syllables bunch up and then stretch out, so
        // successive intervals differ. Note that varying the burst *lengths* alone
        // does not move this number, because peak-to-peak spacing stays symmetric
        // -- the metric is interval variability, and the test says so.
        val metrics = RhythmAnalyzer.analyze(
            syllables(List(10) { 130 }, gapsMs = List(9) { if (it % 2 == 0) 30 else 190 }),
            sampleRate
        )
        assertTrue("nPVI was ${metrics.npvi}", metrics.npvi > 30.0)
    }

    @Test
    fun `uneven spacing scores higher than even spacing`() {
        // The absolute numbers depend on the envelope, but this ordering is the
        // property the feedback actually relies on.
        val even = RhythmAnalyzer.analyze(syllables(List(10) { 150 }), sampleRate)
        val varied = RhythmAnalyzer.analyze(
            syllables(List(10) { 130 }, gapsMs = List(9) { if (it % 2 == 0) 30 else 190 }),
            sampleRate
        )
        assertTrue("even=${even.npvi} varied=${varied.npvi}", varied.npvi > even.npvi)
    }

    @Test
    fun `no verdict is given without enough speech`() {
        // Refusing to judge two syllables is deliberate: a confident claim from
        // almost no evidence would be worse than saying nothing.
        val metrics = RhythmAnalyzer.analyze(syllables(listOf(150, 150)), sampleRate)
        assertNull(metrics.verdict)
    }

    @Test
    fun `speaking rate is positive when there is speech`() {
        val metrics = RhythmAnalyzer.analyze(syllables(List(8) { 150 }), sampleRate)
        assertTrue(metrics.syllablesPerSecond > 0.0)
    }

    @Test
    fun `invalid sample rate is safe`() {
        val metrics = RhythmAnalyzer.analyze(FloatArray(1000) { 0.1f }, 0)
        assertEquals(0, metrics.syllableCount)
    }
}
