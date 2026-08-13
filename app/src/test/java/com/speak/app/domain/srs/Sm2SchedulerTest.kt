package com.speak.app.domain.srs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Sm2SchedulerTest {

    private val today = 20_000L

    @Test
    fun `a new item is due immediately`() {
        val state = Sm2Scheduler.initial(today)
        assertTrue(Sm2Scheduler.isDue(state, today))
        assertEquals(0, state.repetitions)
        assertEquals(ReviewState.INITIAL_EASINESS, state.easiness, 0.0001)
    }

    @Test
    fun `first success schedules one day out`() {
        val next = Sm2Scheduler.schedule(Sm2Scheduler.initial(today), DrillGrade.GOOD, today)
        assertEquals(1, next.repetitions)
        assertEquals(1, next.intervalDays)
        assertEquals(today + 1, next.dueEpochDay)
    }

    @Test
    fun `second success schedules six days out`() {
        var state = Sm2Scheduler.initial(today)
        state = Sm2Scheduler.schedule(state, DrillGrade.GOOD, today)
        state = Sm2Scheduler.schedule(state, DrillGrade.GOOD, today + 1)
        assertEquals(2, state.repetitions)
        assertEquals(6, state.intervalDays)
        assertEquals(today + 7, state.dueEpochDay)
    }

    @Test
    fun `later intervals multiply by easiness`() {
        var state = Sm2Scheduler.initial(today)
        state = Sm2Scheduler.schedule(state, DrillGrade.GOOD, today)
        state = Sm2Scheduler.schedule(state, DrillGrade.GOOD, today + 1)
        val beforeInterval = state.intervalDays
        val easiness = state.easiness
        state = Sm2Scheduler.schedule(state, DrillGrade.GOOD, today + 7)

        assertEquals(3, state.repetitions)
        assertEquals(Math.round(beforeInterval * easiness).toInt(), state.intervalDays)
        assertTrue(state.intervalDays > beforeInterval)
    }

    @Test
    fun `intervals always move forward even when easiness is at the floor`() {
        // With easiness pinned at 1.3, rounding could otherwise leave the interval
        // unchanged and the item would be reviewed forever at the same spacing.
        var state = ReviewState(repetitions = 5, intervalDays = 3, easiness = 1.3, dueEpochDay = today)
        state = Sm2Scheduler.schedule(state, DrillGrade.HESITANT, today)
        assertTrue(state.intervalDays > 3)
    }

    @Test
    fun `a lapse resets repetitions but keeps hard-won easiness`() {
        var state = Sm2Scheduler.initial(today)
        repeat(3) { state = Sm2Scheduler.schedule(state, DrillGrade.GOOD, today) }
        val earnedEasiness = state.easiness

        state = Sm2Scheduler.schedule(state, DrillGrade.MISSED, today + 30)

        assertEquals(0, state.repetitions)
        assertEquals(1, state.intervalDays)
        assertEquals(today + 31, state.dueEpochDay)
        // Easiness drops, but the item does not forget that it has been hard.
        assertTrue(state.easiness < earnedEasiness)
        assertTrue(state.easiness >= 1.3)
    }

    @Test
    fun `easiness never falls below the floor however often it is missed`() {
        var state = Sm2Scheduler.initial(today)
        repeat(20) { state = Sm2Scheduler.schedule(state, DrillGrade.MISSED, today + it) }
        assertTrue(state.easiness >= 1.3)
    }

    @Test
    fun `easy answers grow easiness faster than merely good ones`() {
        val good = Sm2Scheduler.schedule(Sm2Scheduler.initial(today), DrillGrade.GOOD, today)
        val easy = Sm2Scheduler.schedule(Sm2Scheduler.initial(today), DrillGrade.EASY, today)
        assertTrue(easy.easiness > good.easiness)
    }

    @Test
    fun `hesitant still counts as a pass`() {
        assertTrue(DrillGrade.HESITANT.isPass)
        assertFalse(DrillGrade.MISSED.isPass)
        val state = Sm2Scheduler.schedule(Sm2Scheduler.initial(today), DrillGrade.HESITANT, today)
        assertEquals(1, state.repetitions)
    }

    @Test
    fun `interval is capped so items never disappear for years`() {
        var state = Sm2Scheduler.initial(today)
        repeat(30) { state = Sm2Scheduler.schedule(state, DrillGrade.EASY, today) }
        assertTrue(state.intervalDays <= 365)
    }

    @Test
    fun `not due before its date`() {
        val state = Sm2Scheduler.schedule(Sm2Scheduler.initial(today), DrillGrade.GOOD, today)
        assertFalse(Sm2Scheduler.isDue(state, today))
        assertTrue(Sm2Scheduler.isDue(state, today + 1))
    }
}
