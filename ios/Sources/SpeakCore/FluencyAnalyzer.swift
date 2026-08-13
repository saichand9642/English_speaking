import Foundation

/// Fluency measurements for one spoken turn.
public struct FluencyMetrics: Equatable, Sendable {
    public let wordCount: Int
    public let durationMs: Int64
    public let wordsPerMinute: Int
    public let fillerCount: Int
    public let fillerWords: [String]
    public let mistakeCount: Int

    /// Mistakes per hundred words, the rate that makes turns comparable.
    public var mistakesPerHundredWords: Double {
        wordCount == 0 ? 0 : Double(mistakeCount) * 100.0 / Double(wordCount)
    }
}

/// Derives speaking speed and hesitation from a transcript plus its duration.
///
/// Speed is reported honestly: a natural conversational pace in English is roughly
/// 120–150 words a minute, and the progress screen shows the trend rather than
/// scoring it, because faster is not automatically better.
public enum FluencyAnalyzer {

    /// Single words that function as hesitation markers.
    ///
    /// "Like" and "so" are deliberately absent: both have entirely legitimate uses,
    /// and counting them would inflate the number in a way that misleads.
    static let singleWordFillers: Set<String> = [
        "um", "uh", "erm", "er", "ah", "eh", "hmm", "hm", "mmm", "mm", "uhh", "umm"
    ]

    /// Multi-word hesitations, matched on word boundaries.
    static let phraseFillers = [
        "you know", "i mean", "kind of", "sort of", "how to say", "what to say"
    ]

    public static func analyze(_ utterance: Utterance, mistakeCount: Int) -> FluencyMetrics {
        analyze(
            text: utterance.text,
            durationMs: utterance.durationMs,
            wordCountOverride: utterance.wordCount,
            mistakeCount: mistakeCount
        )
    }

    public static func analyze(
        text: String,
        durationMs: Int64,
        wordCountOverride: Int? = nil,
        mistakeCount: Int = 0
    ) -> FluencyMetrics {
        let words = Words.normalised(text)
        let wordCount = wordCountOverride ?? words.count

        var found: [String] = []
        for word in words where singleWordFillers.contains(word) {
            found.append(word)
        }

        let padded = " " + words.joined(separator: " ") + " "
        for phrase in phraseFillers {
            var searchRange = padded.startIndex..<padded.endIndex
            while let hit = padded.range(of: " \(phrase) ", range: searchRange) {
                found.append(phrase)
                // Overlap the trailing space so "you know you know" counts twice.
                searchRange = padded.index(after: hit.lowerBound)..<padded.endIndex
            }
        }

        var wpm = 0
        if durationMs > 0 {
            // Filler sounds are not words spoken; excluding them keeps the pace
            // figure honest for someone who hesitates a lot.
            let fillerSounds = found.filter { singleWordFillers.contains($0) }.count
            let spoken = max(wordCount - fillerSounds, 0)
            wpm = Int(Double(spoken) * 60_000.0 / Double(durationMs))
        }

        return FluencyMetrics(
            wordCount: wordCount,
            durationMs: durationMs,
            wordsPerMinute: wpm,
            fillerCount: found.count,
            fillerWords: found,
            mistakeCount: mistakeCount
        )
    }
}
