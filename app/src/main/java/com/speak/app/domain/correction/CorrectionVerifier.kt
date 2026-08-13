package com.speak.app.domain.correction

import com.speak.app.domain.model.Correction

/**
 * Throws away corrections that a small language model made up.
 *
 * A 1B model asked to find grammar errors will sometimes report a mistake that
 * is not in the sentence at all, or "fix" a fragment by replacing it with
 * itself. The app's promise is that it never invents mistakes, so every claimed
 * error is checked against the actual transcript before it reaches the screen.
 * A correction survives only if the exact fragment it claims to be fixing is
 * really there, and the fix really differs from it.
 */
object CorrectionVerifier {

    /** Longest fragment we will believe as a "wrong part" of one sentence. */
    private const val MAX_FRAGMENT_WORDS = 12

    fun verify(transcript: String, corrections: List<Correction>): List<Correction> {
        val haystack = normaliseWords(transcript)
        if (haystack.isEmpty()) return emptyList()

        val kept = mutableListOf<Correction>()
        val seen = mutableSetOf<String>()

        for (correction in corrections) {
            val wrong = correction.wrong.trim()
            val right = correction.right.trim()

            if (wrong.isEmpty() || right.isEmpty()) continue
            if (correction.explanation.isBlank()) continue

            val wrongWords = normaliseWords(wrong)
            if (wrongWords.isEmpty() || wrongWords.size > MAX_FRAGMENT_WORDS) continue

            // The fragment must genuinely appear in what the learner said.
            if (!containsRun(haystack, wrongWords)) continue

            // A "correction" that changes nothing is noise.
            if (normaliseWords(right) == wrongWords) continue

            // One report per fragment, whichever came first.
            val key = wrongWords.joinToString(" ") + "->" + normaliseWords(right).joinToString(" ")
            if (!seen.add(key)) continue

            kept += correction.copy(wrong = wrong, right = right)
        }
        return kept
    }

    /** True when [needle] appears as a contiguous run of words inside [haystack]. */
    private fun containsRun(haystack: List<String>, needle: List<String>): Boolean {
        if (needle.size > haystack.size) return false
        outer@ for (start in 0..(haystack.size - needle.size)) {
            for (offset in needle.indices) {
                if (haystack[start + offset] != needle[offset]) continue@outer
            }
            return true
        }
        return false
    }

    private fun normaliseWords(text: String): List<String> =
        text.lowercase()
            .split(Regex("""[^\p{L}\p{N}']+"""))
            .filter { it.isNotBlank() }
}
