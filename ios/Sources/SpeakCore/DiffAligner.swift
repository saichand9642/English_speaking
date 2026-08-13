import Foundation

/// Word-level diff between what the learner said and the corrected sentence, used
/// to render the correction inline with strike-throughs.
///
/// Word level is the right granularity: a character diff on "go" -> "went"
/// produces unreadable letter-by-letter noise, while a sentence-level diff loses
/// the point entirely.
public enum DiffAligner {

    public static func align(original: String, corrected: String) -> [DiffSpan] {
        let left = Words.whitespaceTokens(original)
        let right = Words.whitespaceTokens(corrected)

        if left.isEmpty && right.isEmpty { return [] }
        if left.isEmpty {
            return [DiffSpan(text: right.joined(separator: " "), kind: .added)]
        }
        if right.isEmpty {
            return [DiffSpan(text: left.joined(separator: " "), kind: .removed)]
        }

        let anchors = longestCommonSubsequence(left.map(Words.key), right.map(Words.key))

        var spans: [DiffSpan] = []
        var i = 0
        var j = 0
        for (leftIndex, rightIndex) in anchors {
            if i < leftIndex {
                spans.append(
                    DiffSpan(text: left[i..<leftIndex].joined(separator: " "), kind: .removed)
                )
            }
            if j < rightIndex {
                spans.append(
                    DiffSpan(text: right[j..<rightIndex].joined(separator: " "), kind: .added)
                )
            }
            // Keep the corrected spelling for shared words so capitalisation
            // follows the fix rather than the learner's transcript.
            spans.append(DiffSpan(text: right[rightIndex], kind: .unchanged))
            i = leftIndex + 1
            j = rightIndex + 1
        }
        if i < left.count {
            spans.append(DiffSpan(text: left[i...].joined(separator: " "), kind: .removed))
        }
        if j < right.count {
            spans.append(DiffSpan(text: right[j...].joined(separator: " "), kind: .added))
        }

        return merge(spans)
    }

    /// Collapses runs of the same kind so the UI draws one span, not many.
    private static func merge(_ spans: [DiffSpan]) -> [DiffSpan] {
        var out: [DiffSpan] = []
        for span in spans where !span.text.isEmpty {
            if let last = out.last, last.kind == span.kind {
                out[out.count - 1] = DiffSpan(text: last.text + " " + span.text, kind: span.kind)
            } else {
                out.append(span)
            }
        }
        return out
    }

    /// Returns matched index pairs, in order.
    private static func longestCommonSubsequence(
        _ a: [String],
        _ b: [String]
    ) -> [(Int, Int)] {
        let n = a.count
        let m = b.count
        // table[i][j] = LCS length of a[i...] and b[j...]
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
