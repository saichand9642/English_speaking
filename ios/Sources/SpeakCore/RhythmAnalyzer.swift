import Foundation

/// Rhythm measured straight from the waveform.
public struct RhythmMetrics: Equatable, Sendable {
    public static let syllableTimedBelow = 45.0
    public static let stressTimedAbove = 55.0
    public static let minSyllablesForVerdict = 6

    /// Syllable nuclei detected.
    public let syllableCount: Int
    /// Speaking rate over the voiced portion.
    public let syllablesPerSecond: Double
    /// A normalised Pairwise Variability Index computed over successive
    /// *inter-syllable intervals* — the gaps between one syllable peak and the next.
    ///
    /// Note that this is not the published vowel-duration nPVI, which needs
    /// per-vowel onset and offset boundaries that an energy envelope alone cannot
    /// give. Interval variability is what is measurable here, and it responds to the
    /// same thing in practice: English crowds unstressed syllables together and
    /// stretches the stressed ones, so the spacing varies. Speech where every
    /// syllable is given equal time and weight produces a low value.
    public let npvi: Double

    /// True when syllables are close to equally spaced, the syllable-timed pattern.
    public var isSyllableTimed: Bool {
        syllableCount >= Self.minSyllablesForVerdict && npvi < Self.syllableTimedBelow
    }

    /// Nil when there was not enough speech to say anything responsible.
    public var verdict: String? {
        guard syllableCount >= Self.minSyllablesForVerdict else { return nil }
        if npvi < Self.syllableTimedBelow {
            return "Your syllables are coming out at almost equal length. English "
                + "stretches the important syllables and squeezes the rest — try "
                + "leaning on one word per phrase and letting the others shorten."
        }
        if npvi > Self.stressTimedAbove {
            return "Good rhythm: your stressed syllables are clearly longer than the "
                + "unstressed ones, which is what English listeners expect."
        }
        return nil
    }
}

/// Detects syllable nuclei in mono audio and measures how even their spacing is.
///
/// This follows the approach of de Jong and Wempe (2009): find peaks in the smoothed
/// intensity envelope, require each to stand clear of the surrounding dips, and
/// treat the survivors as syllable nuclei. It is the one part of pronunciation
/// feedback that needs no acoustic model at all — rhythm lives in the energy
/// envelope, so it can be measured exactly rather than inferred.
public enum RhythmAnalyzer {

    private static let frameMs = 10
    private static let windowMs = 25

    /// A peak must rise this far above the neighbouring dip to count.
    private static let requiredDipDb = 2.0

    /// Peaks closer together than this are the same syllable.
    private static let minPeakDistanceMs = 80

    /// Anything this far below the loudest frame is treated as silence.
    private static let silenceFloorBelowPeakDb = 25.0

    public static func analyze(samples: [Float], sampleRate: Int) -> RhythmMetrics {
        guard !samples.isEmpty, sampleRate > 0 else {
            return RhythmMetrics(syllableCount: 0, syllablesPerSecond: 0, npvi: 0)
        }

        let hop = sampleRate * frameMs / 1000
        let window = sampleRate * windowMs / 1000
        guard hop > 0, samples.count >= window else {
            return RhythmMetrics(syllableCount: 0, syllablesPerSecond: 0, npvi: 0)
        }

        // ---- intensity envelope in dB ----
        let frameCount = (samples.count - window) / hop + 1
        var intensity = [Double](repeating: 0, count: frameCount)
        for frame in 0..<frameCount {
            let start = frame * hop
            var sum = 0.0
            for index in start..<(start + window) {
                let value = Double(samples[index])
                sum += value * value
            }
            let rms = (sum / Double(window)).squareRoot()
            intensity[frame] = 20.0 * log10(rms + 1e-10)
        }

        let smoothed = smooth(intensity, radius: 2)
        guard let peakDb = smoothed.max() else {
            return RhythmMetrics(syllableCount: 0, syllablesPerSecond: 0, npvi: 0)
        }
        let floorDb = peakDb - silenceFloorBelowPeakDb
        let minPeakGap = minPeakDistanceMs / frameMs

        // ---- candidate peaks ----
        var peaks: [Int] = []
        if smoothed.count > 2 {
            for frame in 1..<(smoothed.count - 1) {
                let value = smoothed[frame]
                if value < floorDb { continue }
                if value < smoothed[frame - 1] || value < smoothed[frame + 1] { continue }
                if let previous = peaks.last, frame - previous < minPeakGap {
                    // Keep whichever of the two is louder.
                    if value > smoothed[previous] { peaks[peaks.count - 1] = frame }
                    continue
                }
                peaks.append(frame)
            }
        }

        // ---- require a genuine dip between neighbouring peaks ----
        var nuclei: [Int] = []
        for (index, peak) in peaks.enumerated() {
            if index == 0 {
                nuclei.append(peak)
                continue
            }
            guard let previous = nuclei.last else { continue }
            let dip = smoothed[min(previous, peak)...max(previous, peak)].min() ?? 0
            let rise = min(smoothed[previous], smoothed[peak]) - dip
            if rise >= requiredDipDb {
                nuclei.append(peak)
            } else if smoothed[peak] > smoothed[previous] {
                nuclei[nuclei.count - 1] = peak
            }
        }

        guard nuclei.count >= 2 else {
            return RhythmMetrics(syllableCount: nuclei.count, syllablesPerSecond: 0, npvi: 0)
        }

        // ---- rate and nPVI ----
        var intervals: [Double] = []
        for index in 0..<(nuclei.count - 1) {
            intervals.append(Double(nuclei[index + 1] - nuclei[index]) * Double(frameMs))
        }
        let voicedMs = Double(nuclei[nuclei.count - 1] - nuclei[0]) * Double(frameMs)
        let rate = voicedMs > 0 ? Double(nuclei.count) * 1000.0 / voicedMs : 0

        var npvi = 0.0
        if intervals.count >= 2 {
            var total = 0.0
            for index in 0..<(intervals.count - 1) {
                let first = intervals[index]
                let second = intervals[index + 1]
                let mean = (first + second) / 2.0
                if mean > 0 { total += abs(first - second) / mean }
            }
            npvi = 100.0 * total / Double(intervals.count - 1)
        }

        return RhythmMetrics(
            syllableCount: nuclei.count,
            syllablesPerSecond: rate,
            npvi: npvi
        )
    }

    private static func smooth(_ values: [Double], radius: Int) -> [Double] {
        guard radius > 0 else { return values }
        var out = [Double](repeating: 0, count: values.count)
        for index in values.indices {
            var sum = 0.0
            var count = 0
            for offset in -radius...radius {
                let at = index + offset
                if at >= 0 && at < values.count {
                    sum += values[at]
                    count += 1
                }
            }
            out[index] = sum / Double(count)
        }
        return out
    }
}
