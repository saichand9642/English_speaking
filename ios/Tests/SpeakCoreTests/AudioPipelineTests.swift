import Foundation
import Testing
@testable import SpeakCore

/// The audio pipeline's decision layer, tested against synthetic signals.
///
/// These are the cases that decide whether a turn ends at the right moment: too
/// eager and it cuts the learner off mid-sentence, too slow and they sit waiting.
/// Both failures are worse than a wrong correction, because they stop the
/// conversation happening at all.
@Suite("Voice activity detector")
struct VoiceActivityDetectorTests {

    private let sampleRate = 16_000
    private let frameSamples = 320 // 20 ms

    /// Deterministic pseudo-random noise, so a failure is always reproducible.
    private func noise(amplitude: Float, seed: UInt64) -> [Float] {
        var state = seed &* 6_364_136_223_846_793_005 &+ 1
        return (0..<frameSamples).map { _ in
            state = state &* 6_364_136_223_846_793_005 &+ 1_442_695_040_888_963_407
            let unit = Float(state >> 40) / Float(1 << 24)
            return (unit - 0.5) * 2 * amplitude
        }
    }

    private func silence(amplitude: Float = 0.0005, seed: UInt64 = 1) -> [Float] {
        noise(amplitude: amplitude, seed: seed)
    }

    /// A voiced tone: high energy, low zero-crossing rate.
    private func tone(amplitude: Float = 0.3, frequency: Double = 140) -> [Float] {
        (0..<frameSamples).map { index in
            Float(Double(amplitude) * sin(2 * Double.pi * frequency * Double(index) / Double(sampleRate)))
        }
    }

    /// A fricative such as "s": low energy but crossing zero constantly.
    private func fricative(amplitude: Float = 0.01) -> [Float] {
        (0..<frameSamples).map { index in
            (index % 2 == 0 ? 1 : -1) * amplitude
        }
    }

    @discardableResult
    private func feed(
        _ detector: VoiceActivityDetector,
        _ frame: () -> [Float],
        count: Int
    ) -> VoiceActivityDetector.State {
        var state = detector.state
        for _ in 0..<count { state = detector.accept(frame()) }
        return state
    }

    private func calibrate(_ detector: VoiceActivityDetector, frames: Int = 14) {
        for _ in 0..<frames { detector.accept(silence()) }
    }

    @Test("starts by calibrating then waits")
    func calibratesThenWaits() {
        let detector = VoiceActivityDetector(sampleRate: sampleRate)
        #expect(detector.state == .calibrating)
        calibrate(detector)
        #expect(detector.state == .waiting)
    }

    @Test("detects speech well above the noise floor")
    func detectsSpeech() {
        let detector = VoiceActivityDetector(sampleRate: sampleRate)
        calibrate(detector)
        #expect(feed(detector, { tone() }, count: 20) == .speaking)
    }

    @Test("ends the turn after trailing silence")
    func endsAfterSilence() {
        let detector = VoiceActivityDetector(sampleRate: sampleRate, trailingSilenceMs: 400)
        calibrate(detector)
        feed(detector, { tone() }, count: 30)
        #expect(detector.state == .speaking)
        // 400 ms of silence at 20 ms a frame is 20 frames.
        #expect(feed(detector, { silence() }, count: 25) == .finished)
    }

    @Test("does not end the turn on a short pause between words")
    func survivesShortPause() {
        let detector = VoiceActivityDetector(sampleRate: sampleRate, trailingSilenceMs: 900)
        calibrate(detector)
        feed(detector, { tone() }, count: 20)
        // A 300 ms gap, as between clauses, must not finish the turn.
        feed(detector, { silence() }, count: 15)
        #expect(detector.state != .finished)
        #expect(feed(detector, { tone() }, count: 10) == .speaking)
    }

    @Test("unvoiced fricatives count as speech")
    func fricativesCount() {
        // Energy alone would clip a trailing "s" off the end of a word, which is
        // exactly where a learner's final consonants need to be heard.
        let detector = VoiceActivityDetector(sampleRate: sampleRate, trailingSilenceMs: 300)
        calibrate(detector)
        feed(detector, { tone() }, count: 20)
        #expect(feed(detector, { fricative() }, count: 12) == .speaking)
    }

    @Test("times out when nobody speaks")
    func timesOut() {
        let detector = VoiceActivityDetector(sampleRate: sampleRate, noSpeechTimeoutMs: 600)
        calibrate(detector)
        #expect(feed(detector, { silence() }, count: 40) == .timedOut)
    }

    @Test("ignores a brief click")
    func ignoresClick() {
        let detector = VoiceActivityDetector(
            sampleRate: sampleRate, minSpeechMs: 250, noSpeechTimeoutMs: 4_000
        )
        calibrate(detector)
        // Two frames is 40 ms: a door or a tap, not a word.
        feed(detector, { tone() }, count: 2)
        feed(detector, { silence() }, count: 10)
        #expect(detector.state == .waiting)
    }

    @Test("adapts to a loud room")
    func adaptsToLoudRoom() {
        // Calibrated against loud background noise, that same noise must not then
        // register as speech. This is what makes it work outdoors without tuning.
        let detector = VoiceActivityDetector(sampleRate: sampleRate)
        for _ in 0..<14 { detector.accept(silence(amplitude: 0.05, seed: 3)) }
        #expect(detector.state == .waiting)
        feed(detector, { silence(amplitude: 0.05, seed: 4) }, count: 10)
        #expect(detector.state == .waiting)
    }

    @Test("a single loud burst during calibration does not deafen the detector")
    func medianNoiseFloor() {
        // The noise floor is a median, so one cough while calibrating cannot drag it
        // up and make real speech undetectable for the rest of the turn.
        let detector = VoiceActivityDetector(sampleRate: sampleRate)
        detector.accept(tone(amplitude: 0.9))
        for _ in 0..<13 { detector.accept(silence()) }
        #expect(detector.state == .waiting)
        #expect(feed(detector, { tone() }, count: 20) == .speaking)
    }

    @Test("speech duration excludes trailing silence")
    func durationExcludesSilence() {
        let detector = VoiceActivityDetector(sampleRate: sampleRate, trailingSilenceMs: 400)
        calibrate(detector)
        feed(detector, { tone() }, count: 50) // 1000 ms of speech
        let before = detector.speechDurationMs()
        feed(detector, { silence() }, count: 25) // finishes the turn
        let after = detector.speechDurationMs()

        // Words per minute would be wrong if padding counted as speaking time.
        #expect(after <= before + 40)
        #expect(after > 0)
    }

    @Test("caps a runaway turn")
    func capsRunaway() {
        let detector = VoiceActivityDetector(
            sampleRate: sampleRate, trailingSilenceMs: 5_000, maxTurnMs: 1_000
        )
        calibrate(detector)
        #expect(feed(detector, { tone() }, count: 100) == .finished)
    }

    @Test("reset returns it to calibrating")
    func resets() {
        let detector = VoiceActivityDetector(sampleRate: sampleRate)
        calibrate(detector)
        feed(detector, { tone() }, count: 20)
        detector.reset()
        #expect(detector.state == .calibrating)
        #expect(detector.speechDurationMs() == 0)
    }

    @Test("level is normalised into zero to one")
    func normalisedLevel() {
        let detector = VoiceActivityDetector(sampleRate: sampleRate)
        calibrate(detector)
        detector.accept(tone(amplitude: 0.8))
        #expect(detector.normalisedLevel >= 0 && detector.normalisedLevel <= 1)
        detector.accept(silence())
        #expect(detector.normalisedLevel >= 0 && detector.normalisedLevel <= 1)
    }

    @Test("an empty frame is ignored")
    func emptyFrame() {
        let detector = VoiceActivityDetector(sampleRate: sampleRate)
        #expect(detector.accept([]) == .calibrating)
    }
}

@Suite("Rhythm analyzer")
struct RhythmAnalyzerTests {

    private let sampleRate = 16_000

    /// Builds a signal of syllable-like energy bursts.
    private func syllables(
        durationsMs: [Int],
        gapMs: Int = 60,
        gapsMs: [Int]? = nil
    ) -> [Float] {
        var samples: [Float] = []
        func appendSilence(_ ms: Int) {
            samples.append(contentsOf: [Float](repeating: 0, count: sampleRate * ms / 1000))
        }
        appendSilence(120)
        for (index, ms) in durationsMs.enumerated() {
            let count = sampleRate * ms / 1000
            for sampleIndex in 0..<count {
                // A smooth envelope, so each burst gives one clear energy peak.
                let phase = Double(sampleIndex) / Double(count)
                let envelope = sin(Double.pi * phase)
                let carrier = sin(2 * Double.pi * 150.0 * Double(sampleIndex) / Double(sampleRate))
                samples.append(Float(0.5 * envelope * carrier))
            }
            if index != durationsMs.count - 1 {
                appendSilence(gapsMs?.indices.contains(index) == true ? gapsMs![index] : gapMs)
            }
        }
        appendSilence(120)
        return samples
    }

    @Test("silence yields nothing")
    func silenceYieldsNothing() {
        let metrics = RhythmAnalyzer.analyze(
            samples: [Float](repeating: 0, count: sampleRate), sampleRate: sampleRate
        )
        #expect(metrics.npvi == 0)
        #expect(metrics.verdict == nil)
    }

    @Test("empty input is safe")
    func emptyIsSafe() {
        let metrics = RhythmAnalyzer.analyze(samples: [], sampleRate: sampleRate)
        #expect(metrics.syllableCount == 0)
        #expect(metrics.syllablesPerSecond == 0)
    }

    @Test("counts syllable nuclei")
    func countsNuclei() {
        let metrics = RhythmAnalyzer.analyze(
            samples: syllables(durationsMs: Array(repeating: 150, count: 8)),
            sampleRate: sampleRate
        )
        // Peak detection on real audio is approximate; within a couple of syllables
        // is enough for the rhythm statistic to be meaningful.
        #expect(
            metrics.syllableCount >= 6 && metrics.syllableCount <= 10,
            "expected about 8, found \(metrics.syllableCount)"
        )
    }

    @Test("evenly spaced syllables give a low nPVI")
    func evenSpacingLowNpvi() {
        // Equal spacing is the syllable-timed pattern this app points out.
        let metrics = RhythmAnalyzer.analyze(
            samples: syllables(durationsMs: Array(repeating: 150, count: 10)),
            sampleRate: sampleRate
        )
        #expect(metrics.npvi < 25, "nPVI was \(metrics.npvi)")
    }

    @Test("unevenly spaced syllables give a high nPVI")
    func unevenSpacingHighNpvi() {
        // The stress-timed pattern: syllables bunch up and then stretch out. Note
        // that varying burst *lengths* alone does not move this number, because
        // peak-to-peak spacing stays symmetric — the metric is interval variability.
        let metrics = RhythmAnalyzer.analyze(
            samples: syllables(
                durationsMs: Array(repeating: 130, count: 10),
                gapsMs: (0..<9).map { $0 % 2 == 0 ? 30 : 190 }
            ),
            sampleRate: sampleRate
        )
        #expect(metrics.npvi > 30, "nPVI was \(metrics.npvi)")
    }

    @Test("uneven spacing scores higher than even spacing")
    func unevenBeatsEven() {
        let even = RhythmAnalyzer.analyze(
            samples: syllables(durationsMs: Array(repeating: 150, count: 10)),
            sampleRate: sampleRate
        )
        let varied = RhythmAnalyzer.analyze(
            samples: syllables(
                durationsMs: Array(repeating: 130, count: 10),
                gapsMs: (0..<9).map { $0 % 2 == 0 ? 30 : 190 }
            ),
            sampleRate: sampleRate
        )
        #expect(varied.npvi > even.npvi, "even=\(even.npvi) varied=\(varied.npvi)")
    }

    @Test("no verdict is given without enough speech")
    func noVerdictWithoutEvidence() {
        // Refusing to judge two syllables is deliberate: a confident claim from
        // almost no evidence would be worse than saying nothing.
        let metrics = RhythmAnalyzer.analyze(
            samples: syllables(durationsMs: [150, 150]), sampleRate: sampleRate
        )
        #expect(metrics.verdict == nil)
    }

    @Test("invalid sample rate is safe")
    func invalidSampleRate() {
        let metrics = RhythmAnalyzer.analyze(
            samples: [Float](repeating: 0.1, count: 1000), sampleRate: 0
        )
        #expect(metrics.syllableCount == 0)
    }
}

@Suite("Fluency analyzer")
struct FluencyAnalyzerTests {

    @Test("computes words per minute")
    func wordsPerMinute() {
        let metrics = FluencyAnalyzer.analyze(
            text: "I went to the market yesterday morning",
            durationMs: 3_000
        )
        #expect(metrics.wordCount == 7)
        // 7 words in 3 seconds is 140 a minute.
        #expect(metrics.wordsPerMinute == 140)
    }

    @Test("zero duration does not divide by zero")
    func zeroDuration() {
        let metrics = FluencyAnalyzer.analyze(text: "hello there", durationMs: 0)
        #expect(metrics.wordsPerMinute == 0)
    }

    @Test("counts hesitation sounds")
    func countsFillers() {
        let metrics = FluencyAnalyzer.analyze(
            text: "um I went uh to the er market", durationMs: 5_000
        )
        #expect(metrics.fillerCount == 3)
    }

    @Test("counts multi-word hesitations")
    func countsPhrases() {
        let metrics = FluencyAnalyzer.analyze(
            text: "it was you know quite good i mean really good", durationMs: 5_000
        )
        #expect(metrics.fillerCount == 2)
    }

    @Test("does not count like or so, which have real uses")
    func doesNotCountAmbiguousWords() {
        // Counting these would inflate the number misleadingly.
        let metrics = FluencyAnalyzer.analyze(
            text: "I like this so I bought it", durationMs: 3_000
        )
        #expect(metrics.fillerCount == 0)
    }

    @Test("filler sounds are excluded from the pace figure")
    func fillersExcludedFromPace() {
        // Someone who hesitates a lot should not appear to speak faster for it.
        let clean = FluencyAnalyzer.analyze(text: "one two three four", durationMs: 2_000)
        let hesitant = FluencyAnalyzer.analyze(
            text: "one um two uh three four", durationMs: 2_000
        )
        #expect(hesitant.wordsPerMinute == clean.wordsPerMinute)
    }

    @Test("mistakes per hundred words is a rate, not a count")
    func mistakeRate() {
        let text = (1...50).map { "word\($0)" }.joined(separator: " ")
        let metrics = FluencyAnalyzer.analyze(text: text, durationMs: 20_000, mistakeCount: 5)
        #expect(metrics.mistakesPerHundredWords == 10.0)
    }

    @Test("empty text is safe")
    func emptyText() {
        let metrics = FluencyAnalyzer.analyze(text: "", durationMs: 1_000)
        #expect(metrics.wordCount == 0)
        #expect(metrics.mistakesPerHundredWords == 0)
    }
}

@Suite("Pronunciation analyzer")
struct PronunciationAnalyzerTests {

    private func word(_ text: String, _ confidence: Float) -> SpokenWord {
        SpokenWord(text: text, confidence: confidence)
    }

    @Test("flags only the least clearly spoken words")
    func flagsUnclearWords() {
        let notes = PronunciationAnalyzer.fromConfidence([
            word("yesterday", 0.95),
            word("thirty", 0.20),
            word("went", 0.90)
        ])
        #expect(notes.count == 1)
        #expect(notes.first?.word == "thirty")
        #expect(notes.first?.evidence == .lowConfidence)
    }

    @Test("very short words are left alone")
    func ignoresShortWords() {
        // Confidence on two-letter words is too noisy to act on.
        let notes = PronunciationAnalyzer.fromConfidence([word("to", 0.1), word("a", 0.1)])
        #expect(notes.isEmpty)
    }

    @Test("scores a perfect read-aloud attempt")
    func perfectReadAloud() {
        let scores = PronunciationAnalyzer.scoreReadAloud(
            target: "I think these three things",
            heard: ["I", "think", "these", "three", "things"].map { word($0, 0.95) }
        )
        #expect(scores.allSatisfy { $0.outcome == .correct })
        #expect(PronunciationAnalyzer.accuracyPercent(scores) == 100)
    }

    @Test("detects a substituted word")
    func detectsSubstitution() {
        let scores = PronunciationAnalyzer.scoreReadAloud(
            target: "I think it is very good",
            heard: ["I", "tink", "it", "is", "wery", "good"].map { word($0, 0.8) }
        )
        let substituted = scores.filter { $0.outcome == .substituted }
        #expect(substituted.count == 2)
        #expect(substituted.map(\.expected).sorted() == ["think", "very"])
    }

    @Test("explains th and v substitutions from the audio")
    func explainsKnownPatterns() {
        // This is the case where the app can be confident, because the target
        // sentence is known and the mismatch was directly observed.
        #expect(IndianEnglishPatterns.explain(expected: "think", heard: "tink") != nil)
        #expect(IndianEnglishPatterns.explain(expected: "very", heard: "wery") != nil)
        #expect(IndianEnglishPatterns.explain(expected: "this", heard: "dis") != nil)
    }

    @Test("does not explain unrelated words as a substitution")
    func rejectsCoincidence() {
        // "elephant" and "very" share a v-ish letter but are nothing alike, so the
        // edit-distance guard must reject it.
        #expect(IndianEnglishPatterns.explain(expected: "very", heard: "elephant") == nil)
        #expect(IndianEnglishPatterns.explain(expected: "think", heard: "think") == nil)
    }

    @Test("detects a missing word")
    func detectsMissingWord() {
        let scores = PronunciationAnalyzer.scoreReadAloud(
            target: "I think these three things are good",
            heard: ["I", "think", "these", "things", "are", "good"].map { word($0, 0.9) }
        )
        #expect(scores.contains { $0.outcome == .missing || $0.outcome == .substituted })
        #expect(PronunciationAnalyzer.accuracyPercent(scores) < 100)
    }

    @Test("produces at most three notes")
    func capsNotes() {
        let scores = PronunciationAnalyzer.scoreReadAloud(
            target: "one two three four five six seven",
            heard: ["wun", "too", "tree", "for", "fife", "siks", "sevn"].map { word($0, 0.4) }
        )
        #expect(PronunciationAnalyzer.notes(for: scores).count <= 3)
    }

    @Test("read-aloud notes are marked as target comparisons")
    func notesCarryEvidence() {
        let scores = PronunciationAnalyzer.scoreReadAloud(
            target: "very good", heard: [word("wery", 0.5), word("good", 0.9)]
        )
        let notes = PronunciationAnalyzer.notes(for: scores)
        #expect(notes.first?.evidence == .targetComparison)
        #expect(notes.first?.heardAs == "wery")
    }

    @Test("empty target yields nothing")
    func emptyTarget() {
        #expect(PronunciationAnalyzer.scoreReadAloud(target: "", heard: []).isEmpty)
        #expect(PronunciationAnalyzer.accuracyPercent([]) == 0)
    }
}
