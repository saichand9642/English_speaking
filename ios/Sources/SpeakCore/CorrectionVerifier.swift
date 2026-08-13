import Foundation

/// Throws away corrections that a small language model made up.
///
/// A 1B model asked to find grammar errors will sometimes report a mistake that is
/// not in the sentence at all, or "fix" a fragment by replacing it with itself. The
/// app's promise is that it never invents mistakes, so every claimed error is
/// checked against the actual transcript before it reaches the screen. A correction
/// survives only if the exact fragment it claims to be fixing is really there, and
/// the fix really differs from it.
public enum CorrectionVerifier {

    /// Longest fragment we will believe as a "wrong part" of one sentence.
    static let maxFragmentWords = 12

    public static func verify(transcript: String, corrections: [Correction]) -> [Correction] {
        let haystack = Words.normalised(transcript)
        guard !haystack.isEmpty else { return [] }

        var kept: [Correction] = []
        var seen = Set<String>()

        for correction in corrections {
            let wrong = correction.wrong.trimmingCharacters(in: .whitespacesAndNewlines)
            let right = correction.right.trimmingCharacters(in: .whitespacesAndNewlines)

            if wrong.isEmpty || right.isEmpty { continue }
            if correction.explanation.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                continue
            }

            let wrongWords = Words.normalised(wrong)
            if wrongWords.isEmpty || wrongWords.count > maxFragmentWords { continue }

            // The fragment must genuinely appear in what the learner said.
            guard Words.containsRun(haystack, wrongWords) else { continue }

            // A "correction" that changes nothing is noise.
            let rightWords = Words.normalised(right)
            if rightWords == wrongWords { continue }

            // One report per fragment, whichever came first.
            let key = wrongWords.joined(separator: " ") + "->" + rightWords.joined(separator: " ")
            guard seen.insert(key).inserted else { continue }

            kept.append(
                Correction(
                    wrong: wrong,
                    right: right,
                    explanation: correction.explanation,
                    category: correction.category
                )
            )
        }
        return kept
    }
}
