package com.speak.app.domain.model

/**
 * One word as the acoustic model heard it.
 *
 * [confidence] is the average token probability whisper reported for this word.
 * It is the app's only genuine per-word acoustic signal, so it is carried all the
 * way from the JNI layer rather than being recomputed from text.
 */
data class SpokenWord(
    val text: String,
    val confidence: Float,
    val startMs: Long,
    val endMs: Long
)

/** A complete spoken turn: the text, the per-word acoustics, and how long it took. */
data class Utterance(
    val text: String,
    val words: List<SpokenWord>,
    val durationMs: Long
) {
    val wordCount: Int get() = words.size

    companion object {
        val EMPTY = Utterance("", emptyList(), 0L)
    }
}
