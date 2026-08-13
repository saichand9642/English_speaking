package com.speak.app.domain.pronunciation

import com.speak.app.domain.model.PronunciationEvidence
import com.speak.app.domain.model.PronunciationNote
import com.speak.app.domain.model.SpokenWord

/** One word of a read-aloud attempt, scored against the target sentence. */
data class WordScore(
    val expected: String,
    val heard: String?,
    val confidence: Float,
    val outcome: Outcome
) {
    enum class Outcome { CORRECT, UNCLEAR, SUBSTITUTED, MISSING }
}

/**
 * Produces pronunciation feedback from acoustic evidence.
 *
 * Two very different levels of certainty are available, and the app is explicit
 * about which one it is using:
 *
 * - In Read-aloud the target sentence is known, so a word that comes back
 *   different is a directly observed substitution. This is where v/w and th
 *   detection genuinely works.
 * - In free conversation there is no target, so the only signal is whisper's
 *   per-word confidence. Low confidence means the acoustic model struggled,
 *   which usually -- but not always -- means the word was unclear. Notes from
 *   this path are marked [PronunciationEvidence.LOW_CONFIDENCE] and worded as
 *   observations, never as verdicts.
 *
 * True per-phoneme scoring would need an acoustic model that exposes phoneme
 * posteriors. whisper.cpp does not, and no free offline Android package does, so
 * that is deliberately not attempted here.
 */
object PronunciationAnalyzer {

    /** Below this average token probability, a word is treated as unclear. */
    const val UNCLEAR_CONFIDENCE = 0.55f

    /** Very short words have unreliable confidence, so they are left alone. */
    private const val MIN_WORD_LENGTH = 3

    private const val MAX_NOTES = 3

    /**
     * Free-conversation path: flag the least clearly spoken words.
     */
    fun fromConfidence(words: List<SpokenWord>): List<PronunciationNote> =
        words.asSequence()
            .filter { it.text.trim { ch -> !ch.isLetter() }.length >= MIN_WORD_LENGTH }
            .filter { it.confidence < UNCLEAR_CONFIDENCE }
            .sortedBy { it.confidence }
            .take(MAX_NOTES)
            .map { word ->
                val clean = word.text.trim { !it.isLetter() }
                PronunciationNote(
                    word = clean,
                    heardAs = null,
                    tip = IndianEnglishPatterns.genericTip(clean.lowercase()),
                    confidence = word.confidence,
                    evidence = PronunciationEvidence.LOW_CONFIDENCE
                )
            }
            .toList()

    /**
     * Read-aloud path: align what was heard against the sentence on screen and
     * score every word.
     */
    fun scoreReadAloud(target: String, heard: List<SpokenWord>): List<WordScore> {
        val expectedWords = tokenise(target)
        if (expectedWords.isEmpty()) return emptyList()
        val heardWords = heard.filter { tokenise(it.text).isNotEmpty() }

        val alignment = align(expectedWords, heardWords.map { normalise(it.text) })

        return expectedWords.mapIndexed { index, expected ->
            val heardIndex = alignment[index]
            if (heardIndex == null) {
                WordScore(expected, null, 0f, WordScore.Outcome.MISSING)
            } else {
                val spoken = heardWords[heardIndex]
                val heardText = normalise(spoken.text)
                when {
                    heardText != normalise(expected) ->
                        WordScore(expected, heardText, spoken.confidence, WordScore.Outcome.SUBSTITUTED)
                    spoken.confidence < UNCLEAR_CONFIDENCE ->
                        WordScore(expected, heardText, spoken.confidence, WordScore.Outcome.UNCLEAR)
                    else ->
                        WordScore(expected, heardText, spoken.confidence, WordScore.Outcome.CORRECT)
                }
            }
        }
    }

    /** Turns read-aloud scores into advice, using the substitution table. */
    fun notesFor(scores: List<WordScore>): List<PronunciationNote> =
        scores.asSequence()
            .filter { it.outcome != WordScore.Outcome.CORRECT }
            .sortedBy { it.confidence }
            .take(MAX_NOTES)
            .map { score ->
                val substitution = score.heard?.let {
                    IndianEnglishPatterns.explain(score.expected, it)
                }
                PronunciationNote(
                    word = score.expected,
                    heardAs = score.heard,
                    tip = substitution?.tip
                        ?: if (score.outcome == WordScore.Outcome.MISSING) {
                            "This word was not heard at all. Say the sentence again a little more slowly."
                        } else {
                            IndianEnglishPatterns.genericTip(score.expected.lowercase())
                        },
                    confidence = score.confidence,
                    evidence = PronunciationEvidence.TARGET_COMPARISON
                )
            }
            .toList()

    /** Percentage of target words spoken clearly and correctly. */
    fun accuracyPercent(scores: List<WordScore>): Int {
        if (scores.isEmpty()) return 0
        val correct = scores.count { it.outcome == WordScore.Outcome.CORRECT }
        return (correct * 100.0 / scores.size).toInt()
    }

    /**
     * Maps each expected word onto a heard word index, or null when it is absent.
     * A longest-common-subsequence anchors the exact matches, then unmatched runs
     * between anchors are paired up in order so substitutions line up with the
     * word they replaced.
     */
    private fun align(expected: List<String>, heard: List<String>): Array<Int?> {
        val result = arrayOfNulls<Int>(expected.size)
        val normalisedExpected = expected.map { normalise(it) }

        val anchors = lcsPairs(normalisedExpected, heard)
        var previousExpected = -1
        var previousHeard = -1

        for ((expectedIndex, heardIndex) in anchors + listOf(expected.size to heard.size)) {
            // Pair off the gap between the previous anchor and this one.
            val expectedGap = (previousExpected + 1) until expectedIndex
            val heardGap = (previousHeard + 1) until heardIndex
            val heardGapList = heardGap.toList()
            expectedGap.forEachIndexed { offset, target ->
                result[target] = heardGapList.getOrNull(offset)
            }
            if (expectedIndex < expected.size) result[expectedIndex] = heardIndex
            previousExpected = expectedIndex
            previousHeard = heardIndex
        }
        return result
    }

    private fun lcsPairs(a: List<String>, b: List<String>): List<Pair<Int, Int>> {
        val table = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.size - 1 downTo 0) {
            for (j in b.size - 1 downTo 0) {
                table[i][j] = if (a[i] == b[j]) table[i + 1][j + 1] + 1
                else maxOf(table[i + 1][j], table[i][j + 1])
            }
        }
        val pairs = mutableListOf<Pair<Int, Int>>()
        var i = 0
        var j = 0
        while (i < a.size && j < b.size) {
            when {
                a[i] == b[j] -> { pairs += i to j; i++; j++ }
                table[i + 1][j] >= table[i][j + 1] -> i++
                else -> j++
            }
        }
        return pairs
    }

    private fun tokenise(text: String): List<String> =
        text.split(Regex("""[^\p{L}\p{N}']+""")).filter { it.isNotBlank() }

    private fun normalise(word: String): String =
        word.lowercase().trim { !it.isLetterOrDigit() && it != '\'' }
}
