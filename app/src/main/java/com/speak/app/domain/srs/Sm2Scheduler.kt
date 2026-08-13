package com.speak.app.domain.srs

import kotlin.math.roundToInt

/** How well the learner handled a drill item. Maps onto SM-2's 0..5 grades. */
enum class DrillGrade(val quality: Int) {
    /** Got it wrong, or could not produce it. */
    MISSED(1),

    /** Got there, but hesitated or needed a second attempt. */
    HESITANT(3),

    /** Correct, at normal speed. */
    GOOD(4),

    /** Immediate and effortless. */
    EASY(5);

    val isPass: Boolean get() = quality >= 3
}

/**
 * Scheduling state for one drill item, in SM-2 terms.
 *
 * @param repetitions consecutive successful reviews; reset to 0 on a lapse
 * @param intervalDays days until the next review
 * @param easiness SM-2's easiness factor, never below 1.3
 * @param dueEpochDay the day this item next becomes due
 */
data class ReviewState(
    val repetitions: Int = 0,
    val intervalDays: Int = 0,
    val easiness: Double = INITIAL_EASINESS,
    val dueEpochDay: Long = 0L
) {
    companion object {
        const val INITIAL_EASINESS = 2.5
    }
}

/**
 * The SM-2 spaced-repetition algorithm.
 *
 * Drills re-test the mistakes this particular speaker actually made, on the
 * schedule SM-2 dictates, rather than picking at random. That is the whole point:
 * the errors a learner repeats are the ones worth spending time on, and they need
 * to come back just as they are about to be forgotten.
 */
object Sm2Scheduler {

    private const val MIN_EASINESS = 1.3

    /** Hard ceiling so an item never disappears for years on end. */
    private const val MAX_INTERVAL_DAYS = 365

    fun schedule(state: ReviewState, grade: DrillGrade, todayEpochDay: Long): ReviewState {
        val quality = grade.quality

        // Easiness moves on every review, pass or fail. This is the standard SM-2
        // response-quality adjustment.
        val delta = 0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02)
        val easiness = (state.easiness + delta).coerceAtLeast(MIN_EASINESS)

        return if (grade.isPass) {
            val repetitions = state.repetitions + 1
            val interval = when (repetitions) {
                1 -> 1
                2 -> 6
                else -> (state.intervalDays * easiness).roundToInt().coerceAtLeast(state.intervalDays + 1)
            }.coerceAtMost(MAX_INTERVAL_DAYS)
            ReviewState(
                repetitions = repetitions,
                intervalDays = interval,
                easiness = easiness,
                dueEpochDay = todayEpochDay + interval
            )
        } else {
            // A lapse sends the item back to the start of the ladder, but the
            // easiness it has earned is kept, so a hard item stays hard.
            ReviewState(
                repetitions = 0,
                intervalDays = 1,
                easiness = easiness,
                dueEpochDay = todayEpochDay + 1
            )
        }
    }

    /** A brand-new item is due immediately. */
    fun initial(todayEpochDay: Long): ReviewState =
        ReviewState(dueEpochDay = todayEpochDay)

    fun isDue(state: ReviewState, todayEpochDay: Long): Boolean =
        state.dueEpochDay <= todayEpochDay
}
