package com.speak.app.data.backup

import android.content.Context
import android.net.Uri
import com.speak.app.data.db.DrillCardEntity
import com.speak.app.data.db.MistakeEntity
import com.speak.app.data.db.PronunciationEventEntity
import com.speak.app.data.db.SessionEntity
import com.speak.app.data.db.SpeakDatabase
import com.speak.app.data.db.TurnEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Exports and imports the whole history as a single JSON file.
 *
 * There is no server and no account, so this file is the only copy of the
 * learner's history that can survive losing the phone. It is deliberately plain,
 * readable JSON rather than a database dump, so it stays useful even if this app
 * stops existing.
 *
 * The Gemini API key is never included: a backup file is something people email
 * to themselves.
 */
class BackupManager(
    private val context: Context,
    private val db: SpeakDatabase
) {
    @Serializable
    data class Backup(
        val formatVersion: Int = FORMAT_VERSION,
        val exportedAt: Long,
        val sessions: List<SessionDto>,
        val turns: List<TurnDto>,
        val mistakes: List<MistakeDto>,
        val drillCards: List<DrillCardDto>,
        val pronunciation: List<PronunciationDto>
    )

    @Serializable
    data class SessionDto(
        val id: Long, val startedAt: Long, val endedAt: Long?,
        val mode: String, val topic: String?
    )

    @Serializable
    data class TurnDto(
        val id: Long, val sessionId: Long, val spokenAt: Long, val epochDay: Long,
        val transcript: String, val correctedSentence: String?, val tutorReply: String?,
        val wordCount: Int, val durationMs: Long, val wordsPerMinute: Int,
        val fillerCount: Int, val mistakeCount: Int
    )

    @Serializable
    data class MistakeDto(
        val id: Long, val turnId: Long, val madeAt: Long, val epochDay: Long,
        val signature: String, val wrong: String, val fixed: String,
        val explanation: String, val category: String, val sentence: String
    )

    @Serializable
    data class DrillCardDto(
        val id: Long, val signature: String, val wrong: String, val fixed: String,
        val explanation: String, val category: String, val promptSentence: String,
        val createdAt: Long, val lastReviewedAt: Long?, val repetitions: Int,
        val intervalDays: Int, val easiness: Double, val dueEpochDay: Long,
        val lapses: Int, val timesSeen: Int
    )

    @Serializable
    data class PronunciationDto(
        val id: Long, val turnId: Long, val notedAt: Long, val epochDay: Long,
        val word: String, val heardAs: String?, val confidence: Float,
        val evidence: String, val tip: String
    )

    data class ImportSummary(
        val turns: Int,
        val mistakes: Int,
        val drillCards: Int
    )

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    suspend fun export(destination: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val backup = Backup(
                exportedAt = System.currentTimeMillis(),
                sessions = db.sessions().all().map {
                    SessionDto(it.id, it.startedAt, it.endedAt, it.mode, it.topic)
                },
                turns = db.turns().all().map {
                    TurnDto(
                        it.id, it.sessionId, it.spokenAt, it.epochDay, it.transcript,
                        it.correctedSentence, it.tutorReply, it.wordCount, it.durationMs,
                        it.wordsPerMinute, it.fillerCount, it.mistakeCount
                    )
                },
                mistakes = db.mistakes().all().map {
                    MistakeDto(
                        it.id, it.turnId, it.madeAt, it.epochDay, it.signature,
                        it.wrong, it.fixed, it.explanation, it.category, it.sentence
                    )
                },
                drillCards = db.drillCards().all().map {
                    DrillCardDto(
                        it.id, it.signature, it.wrong, it.fixed, it.explanation,
                        it.category, it.promptSentence, it.createdAt, it.lastReviewedAt,
                        it.repetitions, it.intervalDays, it.easiness, it.dueEpochDay,
                        it.lapses, it.timesSeen
                    )
                },
                pronunciation = db.pronunciation().all().map {
                    PronunciationDto(
                        it.id, it.turnId, it.notedAt, it.epochDay, it.word,
                        it.heardAs, it.confidence, it.evidence, it.tip
                    )
                }
            )
            val text = json.encodeToString(backup)
            context.contentResolver.openOutputStream(destination, "wt")?.use { stream ->
                stream.write(text.toByteArray())
            } ?: throw IllegalStateException("Could not open the file for writing.")
            backup.turns.size
        }
    }

    /**
     * Merges a backup into the current database.
     *
     * Merge rather than replace, and keyed on signature rather than row id, so
     * importing onto a phone that has already been used adds history instead of
     * destroying it. Ids from the file are deliberately not reused.
     */
    suspend fun import(source: Uri): Result<ImportSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(source)?.use { stream ->
                stream.bufferedReader().readText()
            } ?: throw IllegalStateException("Could not open the file.")

            val backup = json.decodeFromString<Backup>(text)
            if (backup.formatVersion > FORMAT_VERSION) {
                throw IllegalStateException(
                    "This backup was made by a newer version of Speak."
                )
            }

            // Old ids cannot be trusted, so sessions and turns are re-inserted and
            // the mapping from old id to new id is carried down the chain.
            val sessionIdMap = mutableMapOf<Long, Long>()
            for (session in backup.sessions) {
                val newId = db.sessions().insert(
                    SessionEntity(
                        startedAt = session.startedAt,
                        endedAt = session.endedAt,
                        mode = session.mode,
                        topic = session.topic
                    )
                )
                sessionIdMap[session.id] = newId
            }

            val turnIdMap = mutableMapOf<Long, Long>()
            for (turn in backup.turns) {
                val newId = db.turns().insert(
                    TurnEntity(
                        sessionId = sessionIdMap[turn.sessionId] ?: 0L,
                        spokenAt = turn.spokenAt,
                        epochDay = turn.epochDay,
                        transcript = turn.transcript,
                        correctedSentence = turn.correctedSentence,
                        tutorReply = turn.tutorReply,
                        wordCount = turn.wordCount,
                        durationMs = turn.durationMs,
                        wordsPerMinute = turn.wordsPerMinute,
                        fillerCount = turn.fillerCount,
                        mistakeCount = turn.mistakeCount
                    )
                )
                turnIdMap[turn.id] = newId
            }

            db.mistakes().insertAll(
                backup.mistakes.map { mistake ->
                    MistakeEntity(
                        turnId = turnIdMap[mistake.turnId] ?: 0L,
                        madeAt = mistake.madeAt,
                        epochDay = mistake.epochDay,
                        signature = mistake.signature,
                        wrong = mistake.wrong,
                        fixed = mistake.fixed,
                        explanation = mistake.explanation,
                        category = mistake.category,
                        sentence = mistake.sentence
                    )
                }
            )

            db.pronunciation().insertAll(
                backup.pronunciation.map { event ->
                    PronunciationEventEntity(
                        turnId = turnIdMap[event.turnId] ?: 0L,
                        notedAt = event.notedAt,
                        epochDay = event.epochDay,
                        word = event.word,
                        heardAs = event.heardAs,
                        confidence = event.confidence,
                        evidence = event.evidence,
                        tip = event.tip
                    )
                }
            )

            var importedCards = 0
            for (card in backup.drillCards) {
                val existing = db.drillCards().findBySignature(card.signature)
                if (existing == null) {
                    db.drillCards().insert(
                        DrillCardEntity(
                            signature = card.signature,
                            wrong = card.wrong,
                            fixed = card.fixed,
                            explanation = card.explanation,
                            category = card.category,
                            promptSentence = card.promptSentence,
                            createdAt = card.createdAt,
                            lastReviewedAt = card.lastReviewedAt,
                            repetitions = card.repetitions,
                            intervalDays = card.intervalDays,
                            easiness = card.easiness,
                            dueEpochDay = card.dueEpochDay,
                            lapses = card.lapses,
                            timesSeen = card.timesSeen
                        )
                    )
                    importedCards++
                } else {
                    // Same error known on both sides: keep the sooner due date and
                    // the larger seen count, so importing never loses urgency.
                    db.drillCards().update(
                        existing.copy(
                            timesSeen = maxOf(existing.timesSeen, card.timesSeen),
                            dueEpochDay = minOf(existing.dueEpochDay, card.dueEpochDay),
                            lapses = maxOf(existing.lapses, card.lapses)
                        )
                    )
                }
            }

            ImportSummary(
                turns = backup.turns.size,
                mistakes = backup.mistakes.size,
                drillCards = importedCards
            )
        }
    }

    companion object {
        const val FORMAT_VERSION = 1
        const val SUGGESTED_FILENAME = "speak-backup.json"
        const val MIME_TYPE = "application/json"
    }
}
