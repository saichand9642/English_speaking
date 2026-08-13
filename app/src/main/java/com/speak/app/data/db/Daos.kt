package com.speak.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Query("UPDATE sessions SET endedAt = :endedAt WHERE id = :id")
    suspend fun finish(id: Long, endedAt: Long)

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    suspend fun all(): List<SessionEntity>
}

@Dao
interface TurnDao {
    @Insert
    suspend fun insert(turn: TurnEntity): Long

    @Query("SELECT * FROM turns ORDER BY spokenAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<TurnEntity>>

    @Query("SELECT * FROM turns ORDER BY spokenAt ASC")
    suspend fun all(): List<TurnEntity>

    /**
     * Daily aggregates. Words-per-minute is averaged per turn rather than
     * recomputed from totals, so one very long turn cannot dominate the day.
     */
    @Query(
        """
        SELECT epochDay AS epochDay,
               COUNT(*) AS turns,
               SUM(wordCount) AS words,
               SUM(durationMs) AS durationMs,
               AVG(CASE WHEN wordsPerMinute > 0 THEN wordsPerMinute END) AS avgWordsPerMinute,
               SUM(fillerCount) AS fillers,
               SUM(mistakeCount) AS mistakes
        FROM turns
        WHERE epochDay >= :fromEpochDay
        GROUP BY epochDay
        ORDER BY epochDay ASC
        """
    )
    fun dailyStats(fromEpochDay: Long): Flow<List<DailyStats>>

    @Query(
        """
        SELECT COUNT(*) AS turns,
               COALESCE(SUM(wordCount), 0) AS words,
               COALESCE(SUM(durationMs), 0) AS durationMs,
               COALESCE(SUM(mistakeCount), 0) AS mistakes,
               COALESCE(SUM(fillerCount), 0) AS fillers,
               COALESCE(AVG(CASE WHEN wordsPerMinute > 0 THEN wordsPerMinute END), 0.0) AS avgWordsPerMinute,
               COUNT(DISTINCT epochDay) AS activeDays
        FROM turns
        WHERE epochDay BETWEEN :fromEpochDay AND :toEpochDay
        """
    )
    fun totalsBetween(fromEpochDay: Long, toEpochDay: Long): Flow<RangeTotals?>

    /** Distinct days with any activity, newest first, for the streak count. */
    @Query("SELECT DISTINCT epochDay FROM turns ORDER BY epochDay DESC")
    fun activeDays(): Flow<List<Long>>
}

@Dao
interface MistakeDao {
    @Insert
    suspend fun insert(mistake: MistakeEntity): Long

    @Insert
    suspend fun insertAll(mistakes: List<MistakeEntity>)

    @Query("SELECT * FROM mistakes ORDER BY madeAt ASC")
    suspend fun all(): List<MistakeEntity>

    /**
     * The "mistakes you keep making" ranking. Grouped by signature so the same
     * error counts once per occurrence regardless of the sentence it appeared in.
     */
    @Query(
        """
        SELECT signature AS signature,
               wrong AS wrong,
               fixed AS fixed,
               explanation AS explanation,
               category AS category,
               COUNT(*) AS occurrences,
               MAX(madeAt) AS lastMadeAt
        FROM mistakes
        GROUP BY signature
        HAVING COUNT(*) >= :minOccurrences
        ORDER BY occurrences DESC, lastMadeAt DESC
        LIMIT :limit
        """
    )
    fun ranked(minOccurrences: Int = 1, limit: Int = 50): Flow<List<MistakeFrequency>>

    @Query("SELECT COUNT(*) FROM mistakes WHERE epochDay BETWEEN :fromEpochDay AND :toEpochDay")
    fun countBetween(fromEpochDay: Long, toEpochDay: Long): Flow<Int>

    @Query(
        """
        SELECT category AS category, COUNT(*) AS occurrences
        FROM mistakes
        WHERE epochDay >= :fromEpochDay
        GROUP BY category
        ORDER BY occurrences DESC
        """
    )
    fun byCategory(fromEpochDay: Long): Flow<List<CategoryCount>>
}

data class CategoryCount(val category: String, val occurrences: Int)

@Dao
interface DrillCardDao {
    @Query("SELECT * FROM drill_cards WHERE signature = :signature LIMIT 1")
    suspend fun findBySignature(signature: String): DrillCardEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(card: DrillCardEntity): Long

    @Update
    suspend fun update(card: DrillCardEntity)

    @Query("UPDATE drill_cards SET timesSeen = timesSeen + 1 WHERE signature = :signature")
    suspend fun incrementTimesSeen(signature: String)

    /** Cards due today, hardest-earned first. */
    @Query(
        """
        SELECT * FROM drill_cards
        WHERE dueEpochDay <= :todayEpochDay
        ORDER BY timesSeen DESC, dueEpochDay ASC
        LIMIT :limit
        """
    )
    suspend fun due(todayEpochDay: Long, limit: Int = 20): List<DrillCardEntity>

    @Query("SELECT COUNT(*) FROM drill_cards WHERE dueEpochDay <= :todayEpochDay")
    fun dueCount(todayEpochDay: Long): Flow<Int>

    @Query("SELECT * FROM drill_cards ORDER BY timesSeen DESC")
    suspend fun all(): List<DrillCardEntity>

    @Query("SELECT COUNT(*) FROM drill_cards")
    fun total(): Flow<Int>
}

@Dao
interface PronunciationDao {
    @Insert
    suspend fun insertAll(events: List<PronunciationEventEntity>)

    @Query("SELECT * FROM pronunciation_events ORDER BY notedAt ASC")
    suspend fun all(): List<PronunciationEventEntity>

    @Query(
        """
        SELECT word AS word, COUNT(*) AS occurrences, AVG(confidence) AS avgConfidence
        FROM pronunciation_events
        GROUP BY word
        ORDER BY occurrences DESC, avgConfidence ASC
        LIMIT :limit
        """
    )
    fun troublesomeWords(limit: Int = 20): Flow<List<WordDifficulty>>
}

data class WordDifficulty(val word: String, val occurrences: Int, val avgConfidence: Float)
