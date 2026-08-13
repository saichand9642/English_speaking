package com.speak.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One practice session, from opening a mode to leaving it. */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long?,
    /** "conversation", "read_aloud" or "drill". */
    val mode: String,
    val topic: String?
)

/** One spoken turn inside a session. Everything measurable hangs off this. */
@Entity(
    tableName = "turns",
    indices = [Index("sessionId"), Index("spokenAt")]
)
data class TurnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val spokenAt: Long,
    /** The day this turn happened, for streaks and daily charts. */
    val epochDay: Long,
    val transcript: String,
    val correctedSentence: String?,
    val tutorReply: String?,
    val wordCount: Int,
    val durationMs: Long,
    val wordsPerMinute: Int,
    val fillerCount: Int,
    val mistakeCount: Int
)

/**
 * One mistake, logged with a timestamp.
 *
 * [signature] is the normalised identity of the mistake, so the same error made
 * on different days groups together in the "mistakes you keep making" ranking
 * even when the surrounding sentence differs.
 */
@Entity(
    tableName = "mistakes",
    indices = [Index("signature"), Index("madeAt"), Index("turnId")]
)
data class MistakeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val turnId: Long,
    val madeAt: Long,
    val epochDay: Long,
    val signature: String,
    val wrong: String,
    val fixed: String,
    val explanation: String,
    val category: String,
    /** The full sentence it occurred in, so a drill can quote real context. */
    val sentence: String
)

/**
 * A drill item under spaced repetition.
 *
 * Separate from [MistakeEntity] on purpose: mistakes are an append-only log of
 * what happened, while a card is the mutable scheduling state for one recurring
 * error. One card can be backed by many logged mistakes.
 */
@Entity(tableName = "drill_cards", indices = [Index(value = ["signature"], unique = true)])
data class DrillCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val signature: String,
    val wrong: String,
    val fixed: String,
    val explanation: String,
    val category: String,
    /** The sentence the learner will be asked to say correctly. */
    val promptSentence: String,
    val createdAt: Long,
    val lastReviewedAt: Long?,
    // ---- SM-2 state ----
    val repetitions: Int,
    val intervalDays: Int,
    val easiness: Double,
    val dueEpochDay: Long,
    val lapses: Int,
    /** How many times this error has been made in total. */
    val timesSeen: Int
)

/** A word flagged as unclear, with the evidence that flagged it. */
@Entity(tableName = "pronunciation_events", indices = [Index("word"), Index("notedAt")])
data class PronunciationEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val turnId: Long,
    val notedAt: Long,
    val epochDay: Long,
    val word: String,
    val heardAs: String?,
    val confidence: Float,
    /** "target_comparison" or "low_confidence". */
    val evidence: String,
    val tip: String
)

// ---------------------------------------------------------------------------
// Query result holders
// ---------------------------------------------------------------------------

/** A recurring mistake with how often it has happened. */
data class MistakeFrequency(
    val signature: String,
    val wrong: String,
    val fixed: String,
    val explanation: String,
    val category: String,
    val occurrences: Int,
    val lastMadeAt: Long
)

/** Daily aggregates for the progress charts. */
data class DailyStats(
    val epochDay: Long,
    val turns: Int,
    val words: Int,
    val durationMs: Long,
    val avgWordsPerMinute: Double,
    val fillers: Int,
    val mistakes: Int
) {
    val mistakesPerHundredWords: Double
        get() = if (words == 0) 0.0 else mistakes * 100.0 / words

    val fillersPerHundredWords: Double
        get() = if (words == 0) 0.0 else fillers * 100.0 / words
}

/** Totals across a date range, used by the weekly summary. */
data class RangeTotals(
    val turns: Int,
    val words: Int,
    val durationMs: Long,
    val mistakes: Int,
    val fillers: Int,
    val avgWordsPerMinute: Double,
    val activeDays: Int
) {
    val mistakesPerHundredWords: Double
        get() = if (words == 0) 0.0 else mistakes * 100.0 / words

    companion object {
        val EMPTY = RangeTotals(0, 0, 0L, 0, 0, 0.0, 0)
    }
}
