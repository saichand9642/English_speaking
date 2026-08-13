import Foundation

/// The substitutions that most often make Indian-English speech hard for other
/// English speakers to follow, each with a physical instruction rather than a
/// phonetic symbol.
///
/// These are used only where they can be checked: in Read-aloud, where the target
/// sentence is known, a mismatch between the target word and the recognised word can
/// be tested against these patterns directly. That is a real observation about the
/// audio, not a guess from spelling.
public enum IndianEnglishPatterns {

    public struct Substitution: Equatable, Sendable {
        /// The letters in the intended word.
        public let expectedFragment: String
        /// What appears instead when the substitution happens.
        public let heardFragment: String
        public let name: String
        /// A physical instruction: tongue, lips, or stress.
        public let tip: String
    }

    public static let substitutions: [Substitution] = [
        // ---- v / w ----
        Substitution(
            expectedFragment: "v", heardFragment: "w", name: "v sounding like w",
            tip: "For \"v\", press your top teeth lightly onto your bottom lip and hum. "
                + "For \"w\", the lips round and the teeth stay clear."
        ),
        Substitution(
            expectedFragment: "w", heardFragment: "v", name: "w sounding like v",
            tip: "For \"w\", round your lips like a small kiss and keep your teeth away "
                + "from your lip."
        ),

        // ---- th ----
        Substitution(
            expectedFragment: "th", heardFragment: "t", name: "th sounding like t",
            tip: "Let your tongue tip touch the back of your top teeth and push air "
                + "through. Don't tap it like a hard \"t\"."
        ),
        Substitution(
            expectedFragment: "th", heardFragment: "d", name: "th sounding like d",
            tip: "Put your tongue tip gently between your teeth and let it buzz, "
                + "instead of tapping behind the teeth."
        ),
        Substitution(
            expectedFragment: "th", heardFragment: "s", name: "th sounding like s",
            tip: "The tongue must touch the teeth for \"th\". If air hisses past a "
                + "tongue that stays back, it becomes \"s\"."
        ),

        // ---- short and long vowels ----
        Substitution(
            expectedFragment: "ee", heardFragment: "i", name: "long ee cut short",
            tip: "Hold the vowel longer and spread your lips into a slight smile: "
                + "\"sheep\" is much longer than \"ship\"."
        ),
        Substitution(
            expectedFragment: "i", heardFragment: "ee", name: "short i stretched",
            tip: "Keep this vowel short and relax your lips. \"Ship\" is a quick "
                + "sound, not a long one."
        ),
        Substitution(
            expectedFragment: "oo", heardFragment: "u", name: "long oo cut short",
            tip: "Round your lips firmly and hold it: \"fool\" is longer than \"full\"."
        ),
        Substitution(
            expectedFragment: "a", heardFragment: "o", name: "a pulled towards o",
            tip: "Drop your jaw and spread your lips a little for this \"a\", so "
                + "\"cat\" does not drift towards \"cot\"."
        ),

        // ---- s / z ----
        Substitution(
            expectedFragment: "z", heardFragment: "j", name: "z sounding like j",
            tip: "Let the air flow continuously for \"z\" instead of starting it with "
                + "a tongue tap."
        ),
        Substitution(
            expectedFragment: "z", heardFragment: "s", name: "z losing its buzz",
            tip: "Add voice to it: put a hand on your throat and you should feel a "
                + "buzz for \"z\", none for \"s\"."
        ),

        // ---- p / f ----
        Substitution(
            expectedFragment: "f", heardFragment: "p", name: "f sounding like p",
            tip: "For \"f\", rest your top teeth on your bottom lip and let air stream "
                + "out. Don't close your lips fully."
        ),

        // ---- consonant clusters ----
        Substitution(
            expectedFragment: "sk", heardFragment: "isk", name: "extra vowel before a cluster",
            tip: "Start the two consonants together, with no vowel in front of them."
        ),
        Substitution(
            expectedFragment: "st", heardFragment: "ist", name: "extra vowel before a cluster",
            tip: "Begin directly on the \"s\" and slide into the \"t\", with no vowel "
                + "before it."
        )
    ]

    /// Finds the substitution that best explains hearing `heard` when `expected`
    /// was intended, or nil when nothing in the table applies.
    public static func explain(expected: String, heard: String) -> Substitution? {
        let target = Words.key(expected)
        let actual = Words.key(heard)
        guard !target.isEmpty, !actual.isEmpty, target != actual else { return nil }

        return substitutions.first { substitution in
            target.contains(substitution.expectedFragment)
                && actual.contains(substitution.heardFragment)
                // Guard against a coincidental match: the words must be close
                // enough that a single substitution could explain the gap.
                && Words.editDistance(target, actual) <= max(2, target.count / 3)
        }
    }

    /// Generic advice when the audio was unclear but no known pattern fits.
    public static func genericTip(for word: String) -> String {
        let vowels = word.filter { "aeiou".contains($0) }.count
        if word.count >= 8 {
            return "Say it slowly in parts and put the stress on one syllable only. "
                + "Long words lose their shape when every syllable gets equal weight."
        }
        if vowels <= 1 && word.count >= 4 {
            return "Open your mouth a little wider on the vowel and finish the last "
                + "consonant clearly."
        }
        return "Slow down on this word and finish the final sound before moving on."
    }
}

/// One word of a read-aloud attempt, scored against the target sentence.
public struct WordScore: Equatable, Sendable {
    public enum Outcome: Sendable { case correct, unclear, substituted, missing }

    public let expected: String
    public let heard: String?
    public let confidence: Float
    public let outcome: Outcome
}

/// Produces pronunciation feedback from acoustic evidence.
///
/// Two very different levels of certainty are available, and the app is explicit
/// about which one it is using:
///
/// - In Read-aloud the target sentence is known, so a word that comes back different
///   is a directly observed substitution. This is where v/w and th detection
///   genuinely works.
/// - In free conversation there is no target, so the only signal is whisper's
///   per-word confidence. Low confidence means the acoustic model struggled, which
///   usually — but not always — means the word was unclear. Notes from this path are
///   marked `.lowConfidence` and worded as observations, never verdicts.
///
/// True per-phoneme scoring would need an acoustic model that exposes phoneme
/// posteriors. whisper.cpp does not, so that is deliberately not attempted here.
public enum PronunciationAnalyzer {

    /// Below this average token probability, a word is treated as unclear.
    public static let unclearConfidence: Float = 0.55

    /// Very short words have unreliable confidence, so they are left alone.
    static let minWordLength = 3

    static let maxNotes = 3

    /// Free-conversation path: flag the least clearly spoken words.
    public static func fromConfidence(_ words: [SpokenWord]) -> [PronunciationNote] {
        words
            .filter { Words.key($0.text).count >= minWordLength }
            .filter { $0.confidence < unclearConfidence }
            .sorted { $0.confidence < $1.confidence }
            .prefix(maxNotes)
            .map { word in
                let clean = Words.key(word.text)
                return PronunciationNote(
                    word: clean,
                    heardAs: nil,
                    tip: IndianEnglishPatterns.genericTip(for: clean),
                    confidence: word.confidence,
                    evidence: .lowConfidence
                )
            }
    }

    /// Read-aloud path: align what was heard against the sentence on screen and
    /// score every word.
    public static func scoreReadAloud(target: String, heard: [SpokenWord]) -> [WordScore] {
        let expectedWords = Words.normalised(target)
        guard !expectedWords.isEmpty else { return [] }
        let heardWords = heard.filter { !Words.normalised($0.text).isEmpty }

        let alignment = align(expectedWords, heardWords.map { Words.key($0.text) })

        return expectedWords.enumerated().map { index, expected in
            guard let heardIndex = alignment[index] else {
                return WordScore(expected: expected, heard: nil, confidence: 0, outcome: .missing)
            }
            let spoken = heardWords[heardIndex]
            let heardText = Words.key(spoken.text)
            let outcome: WordScore.Outcome
            if heardText != Words.key(expected) {
                outcome = .substituted
            } else if spoken.confidence < unclearConfidence {
                outcome = .unclear
            } else {
                outcome = .correct
            }
            return WordScore(
                expected: expected,
                heard: heardText,
                confidence: spoken.confidence,
                outcome: outcome
            )
        }
    }

    /// Turns read-aloud scores into advice, using the substitution table.
    public static func notes(for scores: [WordScore]) -> [PronunciationNote] {
        scores
            .filter { $0.outcome != .correct }
            .sorted { $0.confidence < $1.confidence }
            .prefix(maxNotes)
            .map { score in
                let substitution = score.heard.flatMap {
                    IndianEnglishPatterns.explain(expected: score.expected, heard: $0)
                }
                let tip: String
                if let substitution {
                    tip = substitution.tip
                } else if score.outcome == .missing {
                    tip = "This word was not heard at all. Say the sentence again a "
                        + "little more slowly."
                } else {
                    tip = IndianEnglishPatterns.genericTip(for: score.expected.lowercased())
                }
                return PronunciationNote(
                    word: score.expected,
                    heardAs: score.heard,
                    tip: tip,
                    confidence: score.confidence,
                    evidence: .targetComparison
                )
            }
    }

    /// Percentage of target words spoken clearly and correctly.
    public static func accuracyPercent(_ scores: [WordScore]) -> Int {
        guard !scores.isEmpty else { return 0 }
        let correct = scores.filter { $0.outcome == .correct }.count
        return Int(Double(correct) * 100.0 / Double(scores.count))
    }

    /// Maps each expected word onto a heard word index, or nil when it is absent.
    ///
    /// A longest-common-subsequence anchors the exact matches, then unmatched runs
    /// between anchors are paired up in order so substitutions line up with the word
    /// they replaced.
    private static func align(_ expected: [String], _ heard: [String]) -> [Int?] {
        var result = [Int?](repeating: nil, count: expected.count)
        let anchors = lcsPairs(expected.map { Words.key($0) }, heard)

        var previousExpected = -1
        var previousHeard = -1

        for (expectedIndex, heardIndex) in anchors + [(expected.count, heard.count)] {
            let expectedGap = Array((previousExpected + 1)..<expectedIndex)
            let heardGap = Array((previousHeard + 1)..<heardIndex)
            for (offset, target) in expectedGap.enumerated() {
                result[target] = offset < heardGap.count ? heardGap[offset] : nil
            }
            if expectedIndex < expected.count { result[expectedIndex] = heardIndex }
            previousExpected = expectedIndex
            previousHeard = heardIndex
        }
        return result
    }

    private static func lcsPairs(_ a: [String], _ b: [String]) -> [(Int, Int)] {
        let n = a.count
        let m = b.count
        var table = [[Int]](repeating: [Int](repeating: 0, count: m + 1), count: n + 1)
        if n > 0 && m > 0 {
            for i in stride(from: n - 1, through: 0, by: -1) {
                for j in stride(from: m - 1, through: 0, by: -1) {
                    table[i][j] = a[i] == b[j]
                        ? table[i + 1][j + 1] + 1
                        : max(table[i + 1][j], table[i][j + 1])
                }
            }
        }
        var pairs: [(Int, Int)] = []
        var i = 0
        var j = 0
        while i < n && j < m {
            if a[i] == b[j] {
                pairs.append((i, j))
                i += 1
                j += 1
            } else if table[i + 1][j] >= table[i][j + 1] {
                i += 1
            } else {
                j += 1
            }
        }
        return pairs
    }
}
