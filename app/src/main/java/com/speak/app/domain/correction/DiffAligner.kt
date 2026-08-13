package com.speak.app.domain.correction

import com.speak.app.domain.model.DiffSpan

/**
 * Word-level diff between what the learner said and the corrected sentence,
 * used to render the correction inline with strike-throughs.
 *
 * A word-level longest-common-subsequence is the right granularity here: a
 * character diff on "go" -> "went" produces unreadable letter-by-letter noise,
 * while a sentence-level diff loses the point entirely.
 */
object DiffAligner {

    private val tokenPattern = Regex("""\s+""")

    fun align(original: String, corrected: String): List<DiffSpan> {
        val left = original.trim().split(tokenPattern).filter { it.isNotEmpty() }
        val right = corrected.trim().split(tokenPattern).filter { it.isNotEmpty() }
        if (left.isEmpty() && right.isEmpty()) return emptyList()
        if (left.isEmpty()) return listOf(DiffSpan(right.joinToString(" "), DiffSpan.Kind.ADDED))
        if (right.isEmpty()) return listOf(DiffSpan(left.joinToString(" "), DiffSpan.Kind.REMOVED))

        val lcs = longestCommonSubsequence(left.map(::normalise), right.map(::normalise))

        val spans = mutableListOf<DiffSpan>()
        var i = 0
        var j = 0
        for ((li, ri) in lcs) {
            if (i < li) spans += DiffSpan(left.subList(i, li).joinToString(" "), DiffSpan.Kind.REMOVED)
            if (j < ri) spans += DiffSpan(right.subList(j, ri).joinToString(" "), DiffSpan.Kind.ADDED)
            // Keep the corrected spelling for shared words so capitalisation follows the fix.
            spans += DiffSpan(right[ri], DiffSpan.Kind.UNCHANGED)
            i = li + 1
            j = ri + 1
        }
        if (i < left.size) spans += DiffSpan(left.subList(i, left.size).joinToString(" "), DiffSpan.Kind.REMOVED)
        if (j < right.size) spans += DiffSpan(right.subList(j, right.size).joinToString(" "), DiffSpan.Kind.ADDED)

        return merge(spans)
    }

    /** Collapses runs of the same kind so the UI draws one span, not many. */
    private fun merge(spans: List<DiffSpan>): List<DiffSpan> {
        val out = mutableListOf<DiffSpan>()
        for (span in spans) {
            if (span.text.isEmpty()) continue
            val last = out.lastOrNull()
            if (last != null && last.kind == span.kind) {
                out[out.lastIndex] = last.copy(text = last.text + " " + span.text)
            } else {
                out += span
            }
        }
        return out
    }

    /** Returns matched index pairs, in order. */
    private fun longestCommonSubsequence(a: List<String>, b: List<String>): List<Pair<Int, Int>> {
        val n = a.size
        val m = b.size
        // table[i][j] = LCS length of a[i..] and b[j..]
        val table = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                table[i][j] = if (a[i] == b[j]) {
                    table[i + 1][j + 1] + 1
                } else {
                    maxOf(table[i + 1][j], table[i][j + 1])
                }
            }
        }
        val pairs = mutableListOf<Pair<Int, Int>>()
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                a[i] == b[j] -> { pairs += i to j; i++; j++ }
                table[i + 1][j] >= table[i][j + 1] -> i++
                else -> j++
            }
        }
        return pairs
    }

    /** Words match if they differ only by case or surrounding punctuation. */
    private fun normalise(word: String): String =
        word.lowercase().trim { !it.isLetterOrDigit() && it != '\'' }
}
