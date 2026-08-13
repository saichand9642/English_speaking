import Foundation

/// Decides when the speaker has finished, so a turn ends on its own rather than
/// needing a second tap.
///
/// The design constraint that matters is the room: this has to work while walking
/// outdoors and in a quiet room, without being configured. So instead of a fixed
/// energy threshold it learns the noise floor from the opening moments of the
/// recording and requires speech to stand a fixed margin above whatever that floor
/// turns out to be.
///
/// A zero-crossing-rate check rides alongside it. Unvoiced consonants like "s" and
/// "f" carry little energy but cross zero often, so energy alone clips them off the
/// end of words — which is exactly where a learner's final consonants need to be
/// heard.
public final class VoiceActivityDetector {

    public enum State: Sendable {
        /// Learning the noise floor.
        case calibrating
        /// Calibrated, waiting for the speaker to begin.
        case waiting
        /// Speech in progress.
        case speaking
        /// Speech ended; the turn is complete.
        case finished
        /// Nobody spoke.
        case timedOut
    }

    private let sampleRate: Int
    private let speechMarginDb: Double
    private let trailingSilenceMs: Int
    private let minSpeechMs: Int
    private let noSpeechTimeoutMs: Int
    private let maxTurnMs: Int

    /// Frames used to learn the noise floor before any decision is made.
    private let calibrationFrames = 12

    private var noiseFrames: [Double] = []
    private var noiseFloorDb = -60.0

    private var elapsedMs = 0
    private var speechMs = 0
    private var silenceMsSinceSpeech = 0
    private var sawSpeech = false

    public private(set) var state: State = .calibrating

    /// Most recent frame level, for the microphone animation.
    public private(set) var lastLevelDb: Double = -100.0

    /// - Parameters:
    ///   - speechMarginDb: speech must exceed the learned noise floor by this much.
    ///   - trailingSilenceMs: silence this long after speech ends the turn.
    ///   - minSpeechMs: ignore bursts shorter than this; they are clicks, not words.
    ///   - noSpeechTimeoutMs: give up waiting for speech to start after this long.
    ///   - maxTurnMs: hard ceiling on one turn.
    public init(
        sampleRate: Int = 16_000,
        speechMarginDb: Double = 9.0,
        trailingSilenceMs: Int = 900,
        minSpeechMs: Int = 250,
        noSpeechTimeoutMs: Int = 8_000,
        maxTurnMs: Int = 60_000
    ) {
        self.sampleRate = sampleRate
        self.speechMarginDb = speechMarginDb
        self.trailingSilenceMs = trailingSilenceMs
        self.minSpeechMs = minSpeechMs
        self.noSpeechTimeoutMs = noSpeechTimeoutMs
        self.maxTurnMs = maxTurnMs
    }

    /// 0...1, suitable for driving a level meter.
    public var normalisedLevel: Float {
        Float(min(max((lastLevelDb - noiseFloorDb) / 30.0, 0.0), 1.0))
    }

    /// Feeds one frame of mono PCM. Frames are expected to be a consistent length;
    /// 20 ms (320 samples at 16 kHz) works well.
    @discardableResult
    public func accept(_ frame: [Float]) -> State {
        if state == .finished || state == .timedOut { return state }
        guard !frame.isEmpty else { return state }

        let frameMs = frame.count * 1000 / sampleRate
        elapsedMs += frameMs

        let db = levelDb(frame)
        lastLevelDb = db

        if state == .calibrating {
            noiseFrames.append(db)
            if noiseFrames.count >= calibrationFrames {
                // Median, not mean: a cough or a door during calibration should not
                // drag the floor up and deafen the detector for the whole turn.
                noiseFloorDb = noiseFrames.sorted()[noiseFrames.count / 2]
                state = .waiting
            }
            return state
        }

        let isSpeech = db > noiseFloorDb + speechMarginDb || isUnvoicedSpeech(frame, db: db)

        if isSpeech {
            speechMs += frameMs
            silenceMsSinceSpeech = 0
            if speechMs >= minSpeechMs {
                sawSpeech = true
                state = .speaking
            }
        } else if sawSpeech {
            silenceMsSinceSpeech += frameMs
            if silenceMsSinceSpeech >= trailingSilenceMs {
                state = .finished
                return state
            }
        } else {
            // Decay the counter so scattered noise never accumulates into "speech".
            speechMs = max(speechMs - frameMs, 0)
        }

        if !sawSpeech && elapsedMs >= noSpeechTimeoutMs {
            state = .timedOut
        } else if elapsedMs >= maxTurnMs {
            state = sawSpeech ? .finished : .timedOut
        }
        return state
    }

    /// Trailing silence is padding, not speech, so the duration used for
    /// words-per-minute excludes it.
    public func speechDurationMs() -> Int {
        max(elapsedMs - silenceMsSinceSpeech, 0)
    }

    public func reset() {
        noiseFrames.removeAll()
        noiseFloorDb = -60.0
        elapsedMs = 0
        speechMs = 0
        silenceMsSinceSpeech = 0
        sawSpeech = false
        lastLevelDb = -100.0
        state = .calibrating
    }

    /// True for quiet but busy frames: fricatives such as "s", "sh" and "f" sit
    /// only a little above the noise floor but cross zero far more often than room
    /// noise does.
    private func isUnvoicedSpeech(_ frame: [Float], db: Double) -> Bool {
        guard db >= noiseFloorDb + 4.0 else { return false }
        var crossings = 0
        for index in 1..<frame.count where (frame[index - 1] < 0) != (frame[index] < 0) {
            crossings += 1
        }
        return Double(crossings) / Double(frame.count) > 0.25
    }

    private func levelDb(_ frame: [Float]) -> Double {
        var sum = 0.0
        for sample in frame {
            sum += Double(sample) * Double(sample)
        }
        let rms = (sum / Double(frame.count)).squareRoot()
        return 20.0 * log10(rms + 1e-10)
    }
}
