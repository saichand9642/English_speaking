package com.speak.app.domain.srs

/**
 * Grades a spoken drill attempt without a language model.
 *
 * Drills are deliberately judged by alignment rather than by asking the tutor
 * model. A drill has one right answer, checking for it is exact, and doing it this
 * way means a review takes about two seconds instead of fifteen -- which is what
 * makes it realistic to get through a queue of them.
 */
object DrillGrader {

    data class Result(val grade: DrillGrade, val usedFix: Boolean, val repeatedMistake: Boolean)

    /**
     * @param spoken what the learner actually said
     * @param expectedFix the corrected fragment they were meant to produce
     * @param originalMistake the fragment they got wrong last time
     */
    fun grade(spoken: String, expectedFix: String, originalMistake: String): Result {
        val said = words(spoken)
        val fix = words(expectedFix)
        val mistake = words(originalMistake)

        val usedFix = fix.isNotEmpty() && containsRun(said, fix)
        val repeated = mistake.isNotEmpty() && containsRun(said, mistake)

        val grade = when {
            // Saying the mistake again is a lapse even if the fix appears too,
            // because the old habit clearly has not been replaced.
            repeated -> DrillGrade.MISSED
            !usedFix -> DrillGrade.MISSED
            // A short, clean answer that goes straight to the fix is effortless
            // recall; a long one usually means they talked their way to it.
            said.size <= fix.size + SHORT_ANSWER_SLACK -> DrillGrade.EASY
            else -> DrillGrade.GOOD
        }
        return Result(grade, usedFix, repeated)
    }

    private const val SHORT_ANSWER_SLACK = 6

    private fun words(text: String): List<String> =
        text.lowercase()
            .split(Regex("""[^\p{L}\p{N}']+"""))
            .filter { it.isNotBlank() }

    private fun containsRun(haystack: List<String>, needle: List<String>): Boolean {
        if (needle.isEmpty() || needle.size > haystack.size) return false
        outer@ for (start in 0..(haystack.size - needle.size)) {
            for (offset in needle.indices) {
                if (haystack[start + offset] != needle[offset]) continue@outer
            }
            return true
        }
        return false
    }
}
