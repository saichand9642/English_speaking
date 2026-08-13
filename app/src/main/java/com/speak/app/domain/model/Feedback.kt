package com.speak.app.domain.model

/**
 * The kinds of mistake the tutor is allowed to report. Keeping this a closed set
 * means the progress screens can group and rank mistakes reliably, and it stops
 * the language model inventing new category names on every turn.
 */
enum class MistakeCategory(val label: String) {
    TENSE("Tense"),
    ARTICLE("A / an / the"),
    PREPOSITION("Preposition"),
    PLURAL("Singular / plural"),
    WORD_ORDER("Word order"),
    WORD_CHOICE("Word choice"),
    OTHER("Other");

    companion object {
        /** Lenient lookup, because a small model will not always echo our exact spelling. */
        fun from(raw: String?): MistakeCategory {
            val key = raw?.trim()?.lowercase()?.replace(' ', '_') ?: return OTHER
            entries.firstOrNull { it.name.lowercase() == key }?.let { return it }
            return when {
                key.contains("tense") || key.contains("verb") -> TENSE
                key.contains("article") -> ARTICLE
                key.contains("prep") -> PREPOSITION
                key.contains("plural") || key.contains("singular") || key.contains("count") -> PLURAL
                key.contains("order") || key.contains("syntax") -> WORD_ORDER
                key.contains("choice") || key.contains("vocab") || key.contains("word") -> WORD_CHOICE
                else -> OTHER
            }
        }
    }
}

/**
 * A single correction: the exact fragment the speaker got wrong, the fix, and a
 * plain-language reason.
 */
data class Correction(
    val wrong: String,
    val right: String,
    val explanation: String,
    val category: MistakeCategory
)

/** Where a pronunciation observation came from. The UI states this honestly. */
enum class PronunciationEvidence {
    /** The word was compared against a known target sentence. Reliable. */
    TARGET_COMPARISON,

    /** The acoustic model was unsure of this word. Suggestive, not conclusive. */
    LOW_CONFIDENCE
}

data class PronunciationNote(
    val word: String,
    /** What the recogniser actually heard, when we can tell. Null if unknown. */
    val heardAs: String?,
    /** A physical instruction: tongue, lips, or stress. */
    val tip: String,
    /** 0f..1f acoustic confidence for this word. */
    val confidence: Float,
    val evidence: PronunciationEvidence
)

/**
 * How a learner's sentence differs from its corrected form, as a flat run of
 * spans. The conversation screen renders [Kind.REMOVED] struck through and
 * [Kind.ADDED] highlighted, inline, so the change is readable at a glance.
 */
data class DiffSpan(val text: String, val kind: Kind) {
    enum class Kind { UNCHANGED, REMOVED, ADDED }
}

/** Everything the tutor produced for one spoken turn. */
data class TutorFeedback(
    /** What the learner actually said, errors intact. */
    val transcript: String,
    /** The whole sentence rewritten correctly, or null when nothing was wrong. */
    val correctedSentence: String?,
    val corrections: List<Correction>,
    /** What the tutor says out loud, including a follow-up question. */
    val spokenReply: String,
    val pronunciation: List<PronunciationNote> = emptyList()
) {
    val wasCorrect: Boolean get() = corrections.isEmpty()
}
