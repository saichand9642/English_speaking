import Foundation

/// Shared word-splitting rules.
///
/// Every comparison in this library — verifying a correction, diffing a sentence,
/// grading a drill — has to agree on what counts as a word and when two words are
/// "the same". Keeping that in one place is what stops a correction being accepted
/// by the verifier and then failing to line up in the diff.
public enum Words {

    /// Splits on anything that is not a letter, digit or apostrophe, and lowercases.
    /// Apostrophes survive so "didn't" stays one word rather than becoming two.
    public static func normalised(_ text: String) -> [String] {
        text.lowercased()
            .split(whereSeparator: { !($0.isLetter || $0.isNumber || $0 == "'") })
            .map(String.init)
    }

    /// Splits on whitespace, preserving the original spelling and punctuation.
    public static func whitespaceTokens(_ text: String) -> [String] {
        text.split(whereSeparator: { $0.isWhitespace }).map(String.init)
    }

    /// Two words match if they differ only by case or surrounding punctuation.
    public static func key(_ word: String) -> String {
        String(
            word.lowercased()
                .drop(while: { !($0.isLetter || $0.isNumber) })
                .reversed()
                .drop(while: { !($0.isLetter || $0.isNumber || $0 == "'") })
                .reversed()
        )
    }

    /// True when `needle` appears as a contiguous run of words inside `haystack`.
    public static func containsRun(_ haystack: [String], _ needle: [String]) -> Bool {
        guard !needle.isEmpty, needle.count <= haystack.count else { return false }
        for start in 0...(haystack.count - needle.count) {
            var matched = true
            for offset in needle.indices where haystack[start + offset] != needle[offset] {
                matched = false
                break
            }
            if matched { return true }
        }
        return false
    }

    /// Levenshtein distance, used to reject coincidental pronunciation matches.
    public static func editDistance(_ a: String, _ b: String) -> Int {
        if a == b { return 0 }
        let left = Array(a)
        let right = Array(b)
        if left.isEmpty { return right.count }
        if right.isEmpty { return left.count }

        var previous = Array(0...right.count)
        var current = [Int](repeating: 0, count: right.count + 1)

        for i in 1...left.count {
            current[0] = i
            for j in 1...right.count {
                let cost = left[i - 1] == right[j - 1] ? 0 : 1
                current[j] = min(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            swap(&previous, &current)
        }
        return previous[right.count]
    }
}
