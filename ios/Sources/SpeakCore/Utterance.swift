import Foundation

/// One word as the acoustic model heard it.
///
/// `confidence` is the average token probability whisper reported for this word.
/// It is the app's only genuine per-word acoustic signal, so it is carried all the
/// way from the C layer rather than being recomputed from text.
public struct SpokenWord: Equatable, Sendable {
    public let text: String
    public let confidence: Float
    public let startMs: Int64
    public let endMs: Int64

    public init(text: String, confidence: Float, startMs: Int64 = 0, endMs: Int64 = 0) {
        self.text = text
        self.confidence = confidence
        self.startMs = startMs
        self.endMs = endMs
    }
}

/// A complete spoken turn: the text, the per-word acoustics, and how long it took.
public struct Utterance: Equatable, Sendable {
    public let text: String
    public let words: [SpokenWord]
    public let durationMs: Int64

    public init(text: String, words: [SpokenWord], durationMs: Int64) {
        self.text = text
        self.words = words
        self.durationMs = durationMs
    }

    public var wordCount: Int { words.count }

    public static let empty = Utterance(text: "", words: [], durationMs: 0)
}
