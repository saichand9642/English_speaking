package com.speak.app.domain.pronunciation

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Rhythm measured straight from the waveform.
 *
 * @param syllableCount syllable nuclei detected
 * @param syllablesPerSecond speaking rate over the voiced portion
 * @param npvi a normalised Pairwise Variability Index computed over successive
 *   *inter-syllable intervals* -- the gaps between one syllable peak and the
 *   next. Note that this is not the published vowel-duration nPVI, which needs
 *   per-vowel onset and offset boundaries that an energy envelope alone cannot
 *   give. Interval variability is what is measurable here, and it responds to the
 *   same thing in practice: English crowds unstressed syllables together and
 *   stretches the stressed ones, so the spacing varies. Speech where every
 *   syllable is given equal time and weight produces a low value.
 */
data class RhythmMetrics(
    val syllableCount: Int,
    val syllablesPerSecond: Double,
    val npvi: Double
) {
    /** True when syllables are close to equal length, the syllable-timed pattern. */
    val isSyllableTimed: Boolean get() = syllableCount >= MIN_SYLLABLES_FOR_VERDICT && npvi < SYLLABLE_TIMED_BELOW

    /** Null when there was not enough speech to say anything responsible. */
    val verdict: String?
        get() = when {
            syllableCount < MIN_SYLLABLES_FOR_VERDICT -> null
            npvi < SYLLABLE_TIMED_BELOW ->
                "Your syllables are coming out at almost equal length. English stretches the important syllables and squeezes the rest -- try leaning on one word per phrase and letting the others shorten."
            npvi > STRESS_TIMED_ABOVE ->
                "Good rhythm: your stressed syllables are clearly longer than the unstressed ones, which is what English listeners expect."
            else -> null
        }

    companion object {
        const val SYLLABLE_TIMED_BELOW = 45.0
        const val STRESS_TIMED_ABOVE = 55.0
        const val MIN_SYLLABLES_FOR_VERDICT = 6
    }
}

/**
 * Detects syllable nuclei in 16 kHz mono audio and measures how even their
 * spacing is.
 *
 * This follows the approach of de Jong and Wempe (2009): find peaks in the
 * smoothed intensity envelope, require each to stand clear of the surrounding
 * dips, and treat the survivors as syllable nuclei. It is the one part of
 * pronunciation feedback that needs no acoustic model at all -- rhythm lives in
 * the energy envelope, so it can be measured exactly rather than inferred.
 */
object RhythmAnalyzer {

    private const val FRAME_MS = 10
    private const val WINDOW_MS = 25

    /** A peak must rise this far above the neighbouring dip to count. */
    private const val REQUIRED_DIP_DB = 2.0

    /** Peaks closer together than this are the same syllable. */
    private const val MIN_PEAK_DISTANCE_MS = 80

    /** Anything this far below the loudest frame is treated as silence. */
    private const val SILENCE_FLOOR_BELOW_PEAK_DB = 25.0

    fun analyze(samples: FloatArray, sampleRate: Int): RhythmMetrics {
        if (samples.isEmpty() || sampleRate <= 0) return RhythmMetrics(0, 0.0, 0.0)

        val hop = sampleRate * FRAME_MS / 1000
        val window = sampleRate * WINDOW_MS / 1000
        if (hop <= 0 || samples.size < window) return RhythmMetrics(0, 0.0, 0.0)

        // ---- intensity envelope in dB ----
        val frameCount = (samples.size - window) / hop + 1
        val intensity = DoubleArray(frameCount)
        for (frame in 0 until frameCount) {
            val start = frame * hop
            var sum = 0.0
            for (index in start until start + window) {
                val value = samples[index].toDouble()
                sum += value * value
            }
            val rms = sqrt(sum / window)
            intensity[frame] = 20.0 * log10(rms + 1e-10)
        }

        val smoothed = smooth(intensity, radius = 2)
        val peakDb = smoothed.max()
        val floorDb = peakDb - SILENCE_FLOOR_BELOW_PEAK_DB
        val minPeakGap = MIN_PEAK_DISTANCE_MS / FRAME_MS

        // ---- candidate peaks ----
        val peaks = mutableListOf<Int>()
        for (frame in 1 until smoothed.size - 1) {
            val value = smoothed[frame]
            if (value < floorDb) continue
            if (value < smoothed[frame - 1] || value < smoothed[frame + 1]) continue
            val previousPeak = peaks.lastOrNull()
            if (previousPeak != null && frame - previousPeak < minPeakGap) {
                // Keep whichever of the two is louder.
                if (value > smoothed[previousPeak]) peaks[peaks.lastIndex] = frame
                continue
            }
            peaks += frame
        }

        // ---- require a genuine dip between neighbouring peaks ----
        val nuclei = mutableListOf<Int>()
        for ((index, peak) in peaks.withIndex()) {
            if (index == 0) { nuclei += peak; continue }
            val previous = nuclei.lastOrNull() ?: peak
            val dip = (previous..peak).minOfOrNull { smoothed[it] } ?: continue
            val rise = minOf(smoothed[previous], smoothed[peak]) - dip
            if (rise >= REQUIRED_DIP_DB) {
                nuclei += peak
            } else if (smoothed[peak] > smoothed[previous]) {
                nuclei[nuclei.lastIndex] = peak
            }
        }

        if (nuclei.size < 2) {
            return RhythmMetrics(nuclei.size, 0.0, 0.0)
        }

        // ---- rate and nPVI ----
        val intervals = DoubleArray(nuclei.size - 1) { (nuclei[it + 1] - nuclei[it]) * FRAME_MS.toDouble() }
        val voicedMs = (nuclei.last() - nuclei.first()) * FRAME_MS.toDouble()
        val rate = if (voicedMs > 0) nuclei.size * 1000.0 / voicedMs else 0.0

        val npvi = if (intervals.size < 2) 0.0 else {
            var total = 0.0
            for (index in 0 until intervals.size - 1) {
                val first = intervals[index]
                val second = intervals[index + 1]
                val mean = (first + second) / 2.0
                if (mean > 0) total += abs(first - second) / mean
            }
            100.0 * total / (intervals.size - 1)
        }

        return RhythmMetrics(nuclei.size, rate, npvi)
    }

    private fun smooth(values: DoubleArray, radius: Int): DoubleArray {
        if (radius <= 0) return values
        val out = DoubleArray(values.size)
        for (index in values.indices) {
            var sum = 0.0
            var count = 0
            for (offset in -radius..radius) {
                val at = index + offset
                if (at in values.indices) { sum += values[at]; count++ }
            }
            out[index] = sum / count
        }
        return out
    }
}
