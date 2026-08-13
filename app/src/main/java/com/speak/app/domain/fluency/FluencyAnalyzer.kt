package com.speak.app.domain.fluency

import com.speak.app.domain.model.Utterance

/** Fluency measurements for one spoken turn. */
data class FluencyMetrics(
    val wordCount: Int,
    val durationMs: Long,
    val wordsPerMinute: Int,
    val fillerCount: Int,
    val fillerWords: List<String>,
    val mistakeCount: Int
) {
    /** Mistakes per hundred words, the rate that makes turns comparable. */
    val mistakesPerHundredWords: Double
        get() = if (wordCount == 0) 0.0 else mistakeCount * 100.0 / wordCount
}

/**
 * Derives speaking speed and hesitation from a transcript plus its duration.
 *
 * Speed is reported honestly: a natural conversational pace in English is roughly
 * 120-150 words a minute, and the progress screen shows the trend rather than
 * scoring it, because faster is not automatically better.
 */
object FluencyAnalyzer {

    /**
     * Single words that function as hesitation markers. "Like" and "so" are
     * deliberately absent: both have entirely legitimate uses, and counting them
     * would inflate the number in a way that misleads.
     */
    private val singleWordFillers = setOf(
        "um", "uh", "erm", "er", "ah", "eh", "hmm", "hm", "mmm", "mm", "uhh", "umm"
    )

    /** Multi-word hesitations, matched on word boundaries. */
    private val phraseFillers = listOf(
        "you know", "i mean", "kind of", "sort of", "how to say", "what to say"
    )

    private val wordSplitter = Regex("""[^\p{L}\p{N}']+""")

    fun analyze(utterance: Utterance, mistakeCount: Int): FluencyMetrics =
        analyze(utterance.text, utterance.durationMs, utterance.wordCount, mistakeCount)

    fun analyze(
        text: String,
        durationMs: Long,
        wordCountOverride: Int? = null,
        mistakeCount: Int = 0
    ): FluencyMetrics {
        val words = text.lowercase().split(wordSplitter).filter { it.isNotBlank() }
        val wordCount = wordCountOverride ?: words.size

        val found = mutableListOf<String>()
        for (word in words) {
            if (word in singleWordFillers) found += word
        }
        val lowered = " " + words.joinToString(" ") + " "
        for (phrase in phraseFillers) {
            var index = lowered.indexOf(" $phrase ")
            while (index >= 0) {
                found += phrase
                index = lowered.indexOf(" $phrase ", index + 1)
            }
        }

        val wpm = if (durationMs <= 0L) 0 else {
            // Filler sounds are not words spoken; excluding them keeps the pace
            // figure honest for someone who hesitates a lot.
            val spoken = (wordCount - found.count { it in singleWordFillers }).coerceAtLeast(0)
            (spoken * 60_000.0 / durationMs).toInt()
        }

        return FluencyMetrics(
            wordCount = wordCount,
            durationMs = durationMs,
            wordsPerMinute = wpm,
            fillerCount = found.size,
            fillerWords = found,
            mistakeCount = mistakeCount
        )
    }
}
