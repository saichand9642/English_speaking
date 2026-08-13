package com.speak.app.data.repo

import com.speak.app.data.db.CategoryCount
import com.speak.app.data.db.DailyStats
import com.speak.app.data.db.DrillCardEntity
import com.speak.app.data.db.MistakeEntity
import com.speak.app.data.db.MistakeFrequency
import com.speak.app.data.db.PronunciationEventEntity
import com.speak.app.data.db.RangeTotals
import com.speak.app.data.db.SessionEntity
import com.speak.app.data.db.SpeakDatabase
import com.speak.app.data.db.TurnEntity
import com.speak.app.data.db.WordDifficulty
import com.speak.app.domain.fluency.FluencyMetrics
import com.speak.app.domain.model.Correction
import com.speak.app.domain.model.PronunciationEvidence
import com.speak.app.domain.model.PronunciationNote
import com.speak.app.domain.srs.DrillGrade
import com.speak.app.domain.srs.ReviewState
import com.speak.app.domain.srs.Sm2Scheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

/**
 * Everything the app stores, stored locally and nowhere else.
 *
 * Saving a turn does three things at once: it appends to the mistake log,
 * increments the drill card for each recurring error, and records the
 * measurements the progress charts read. Keeping that in one place means a turn
 * can never be half-recorded.
 */
class PracticeRepository(private val db: SpeakDatabase) {

    // ---- sessions ----

    suspend fun startSession(mode: String, topic: String?): Long =
        db.sessions().insert(
            SessionEntity(startedAt = now(), endedAt = null, mode = mode, topic = topic)
        )

    suspend fun endSession(id: Long) = db.sessions().finish(id, now())

    // ---- recording a turn ----

    /**
     * Persists one spoken turn and everything derived from it.
     *
     * @return the new turn's id.
     */
    suspend fun recordTurn(
        sessionId: Long,
        transcript: String,
        correctedSentence: String?,
        tutorReply: String?,
        metrics: FluencyMetrics,
        corrections: List<Correction>,
        pronunciation: List<PronunciationNote>
    ): Long {
        val timestamp = now()
        val day = today()

        val turnId = db.turns().insert(
            TurnEntity(
                sessionId = sessionId,
                spokenAt = timestamp,
                epochDay = day,
                transcript = transcript,
                correctedSentence = correctedSentence,
                tutorReply = tutorReply,
                wordCount = metrics.wordCount,
                durationMs = metrics.durationMs,
                wordsPerMinute = metrics.wordsPerMinute,
                fillerCount = metrics.fillerCount,
                mistakeCount = corrections.size
            )
        )

        if (corrections.isNotEmpty()) {
            db.mistakes().insertAll(
                corrections.map { correction ->
                    MistakeEntity(
                        turnId = turnId,
                        madeAt = timestamp,
                        epochDay = day,
                        signature = signatureOf(correction),
                        wrong = correction.wrong,
                        fixed = correction.right,
                        explanation = correction.explanation,
                        category = correction.category.name,
                        sentence = transcript
                    )
                }
            )
            for (correction in corrections) upsertDrillCard(correction, transcript, timestamp, day)
        }

        if (pronunciation.isNotEmpty()) {
            db.pronunciation().insertAll(
                pronunciation.map { note ->
                    PronunciationEventEntity(
                        turnId = turnId,
                        notedAt = timestamp,
                        epochDay = day,
                        word = note.word.lowercase(),
                        heardAs = note.heardAs,
                        confidence = note.confidence,
                        evidence = when (note.evidence) {
                            PronunciationEvidence.TARGET_COMPARISON -> "target_comparison"
                            PronunciationEvidence.LOW_CONFIDENCE -> "low_confidence"
                        },
                        tip = note.tip
                    )
                }
            )
        }
        return turnId
    }

    /**
     * A mistake made again is not a new card: it is the same card, now overdue.
     * Repeat offenders therefore rise to the top of the drill queue naturally.
     */
    private suspend fun upsertDrillCard(
        correction: Correction,
        sentence: String,
        timestamp: Long,
        day: Long
    ) {
        val signature = signatureOf(correction)
        val existing = db.drillCards().findBySignature(signature)
        if (existing == null) {
            val initial = Sm2Scheduler.initial(day)
            db.drillCards().insert(
                DrillCardEntity(
                    signature = signature,
                    wrong = correction.wrong,
                    fixed = correction.right,
                    explanation = correction.explanation,
                    category = correction.category.name,
                    promptSentence = sentence,
                    createdAt = timestamp,
                    lastReviewedAt = null,
                    repetitions = initial.repetitions,
                    intervalDays = initial.intervalDays,
                    easiness = initial.easiness,
                    dueEpochDay = initial.dueEpochDay,
                    lapses = 0,
                    timesSeen = 1
                )
            )
        } else {
            db.drillCards().update(
                existing.copy(
                    timesSeen = existing.timesSeen + 1,
                    // Making the mistake again means it is not learned, so it
                    // becomes due immediately regardless of its old schedule.
                    dueEpochDay = minOf(existing.dueEpochDay, day),
                    repetitions = 0,
                    intervalDays = 1,
                    lapses = existing.lapses + 1
                )
            )
        }
    }

    // ---- drills ----

    suspend fun dueDrills(limit: Int = 20): List<DrillCardEntity> =
        db.drillCards().due(today(), limit)

    fun dueDrillCount(): Flow<Int> = db.drillCards().dueCount(today())

    fun totalDrillCount(): Flow<Int> = db.drillCards().total()

    suspend fun gradeDrill(card: DrillCardEntity, grade: DrillGrade) {
        val next = Sm2Scheduler.schedule(
            ReviewState(
                repetitions = card.repetitions,
                intervalDays = card.intervalDays,
                easiness = card.easiness,
                dueEpochDay = card.dueEpochDay
            ),
            grade,
            today()
        )
        db.drillCards().update(
            card.copy(
                repetitions = next.repetitions,
                intervalDays = next.intervalDays,
                easiness = next.easiness,
                dueEpochDay = next.dueEpochDay,
                lapses = if (grade.isPass) card.lapses else card.lapses + 1,
                lastReviewedAt = now()
            )
        )
    }

    // ---- progress ----

    fun rankedMistakes(minOccurrences: Int = 1, limit: Int = 50): Flow<List<MistakeFrequency>> =
        db.mistakes().ranked(minOccurrences, limit)

    fun dailyStats(days: Int = 30): Flow<List<DailyStats>> =
        db.turns().dailyStats(today() - days)

    fun categoryBreakdown(days: Int = 30): Flow<List<CategoryCount>> =
        db.mistakes().byCategory(today() - days)

    fun troublesomeWords(limit: Int = 20): Flow<List<WordDifficulty>> =
        db.pronunciation().troublesomeWords(limit)

    fun weekTotals(weeksAgo: Int = 0): Flow<RangeTotals> {
        val endOfWeek = today() - (weeksAgo * 7L)
        val startOfWeek = endOfWeek - 6
        return db.turns().totalsBetween(startOfWeek, endOfWeek).map { it ?: RangeTotals.EMPTY }
    }

    fun recentTurns(limit: Int = 50): Flow<List<TurnEntity>> = db.turns().recent(limit)

    /**
     * Consecutive days of practice ending today or yesterday.
     *
     * Yesterday counts as still-alive on purpose: a streak that dies at midnight
     * turns a learning tool into a source of guilt, which is the opposite of what
     * this app is for.
     */
    fun streak(): Flow<Int> = db.turns().activeDays().map { days ->
        if (days.isEmpty()) return@map 0
        val today = today()
        val newest = days.first()
        if (newest < today - 1) return@map 0
        var streak = 1
        var expected = newest - 1
        for (day in days.drop(1)) {
            if (day == expected) { streak++; expected-- } else if (day < expected) break
        }
        streak
    }

    companion object {
        /**
         * Identity of a mistake, independent of the sentence it appeared in, so
         * "I go yesterday" and "I go last week" count as the same recurring error.
         */
        fun signatureOf(correction: Correction): String {
            fun normalise(text: String) = text.lowercase()
                .split(Regex("""[^\p{L}\p{N}']+"""))
                .filter { it.isNotBlank() }
                .joinToString(" ")
            return "${correction.category.name}|${normalise(correction.wrong)}|${normalise(correction.right)}"
        }

        fun now(): Long = System.currentTimeMillis()

        fun today(): Long = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
    }
}
