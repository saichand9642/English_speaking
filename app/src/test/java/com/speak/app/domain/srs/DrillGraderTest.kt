package com.speak.app.domain.srs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillGraderTest {

    @Test
    fun `using the fix passes`() {
        val result = DrillGrader.grade(
            spoken = "Yesterday I went to the market",
            expectedFix = "I went",
            originalMistake = "I go"
        )
        assertTrue(result.usedFix)
        assertFalse(result.repeatedMistake)
        assertTrue(result.grade.isPass)
    }

    @Test
    fun `repeating the old mistake fails even if the fix is also present`() {
        // Saying both means the habit has not been replaced yet.
        val result = DrillGrader.grade(
            spoken = "I go yesterday, sorry, I went yesterday",
            expectedFix = "I went",
            originalMistake = "I go"
        )
        assertTrue(result.usedFix)
        assertTrue(result.repeatedMistake)
        assertEquals(DrillGrade.MISSED, result.grade)
    }

    @Test
    fun `missing the fix fails`() {
        val result = DrillGrader.grade(
            spoken = "I visited the market",
            expectedFix = "I went",
            originalMistake = "I go"
        )
        assertFalse(result.usedFix)
        assertEquals(DrillGrade.MISSED, result.grade)
    }

    @Test
    fun `a short direct answer is graded easy`() {
        val result = DrillGrader.grade(
            spoken = "I went to the market",
            expectedFix = "I went",
            originalMistake = "I go"
        )
        assertEquals(DrillGrade.EASY, result.grade)
    }

    @Test
    fun `a long rambling answer that gets there is graded good`() {
        val result = DrillGrader.grade(
            spoken = "Well let me think about it, so the thing is that " +
                "actually in the end I went to the market with my brother",
            expectedFix = "I went",
            originalMistake = "I go"
        )
        assertEquals(DrillGrade.GOOD, result.grade)
    }

    @Test
    fun `matching ignores case and punctuation`() {
        val result = DrillGrader.grade(
            spoken = "Yesterday, I WENT!",
            expectedFix = "i went",
            originalMistake = "i go"
        )
        assertTrue(result.usedFix)
    }

    @Test
    fun `empty speech fails`() {
        val result = DrillGrader.grade("", "I went", "I go")
        assertEquals(DrillGrade.MISSED, result.grade)
    }
}
