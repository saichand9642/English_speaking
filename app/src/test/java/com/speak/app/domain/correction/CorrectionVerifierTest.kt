package com.speak.app.domain.correction

import com.speak.app.domain.model.Correction
import com.speak.app.domain.model.MistakeCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The verifier is the app's guarantee that it never invents mistakes, so these
 * tests are about what it *rejects* at least as much as what it keeps.
 */
class CorrectionVerifierTest {

    private fun correction(
        wrong: String,
        right: String,
        why: String = "Because.",
        category: MistakeCategory = MistakeCategory.TENSE
    ) = Correction(wrong, right, why, category)

    @Test
    fun `keeps a correction whose fragment really appears`() {
        val result = CorrectionVerifier.verify(
            "Yesterday I go to the market",
            listOf(correction("I go", "I went"))
        )
        assertEquals(1, result.size)
        assertEquals("I go", result.first().wrong)
    }

    @Test
    fun `rejects a fragment the speaker never said`() {
        // The classic small-model hallucination: a plausible error, but not this
        // learner's error. It must never reach the screen.
        val result = CorrectionVerifier.verify(
            "Yesterday I went to the market",
            listOf(correction("I goes", "I went"))
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `rejects a correction that changes nothing`() {
        val result = CorrectionVerifier.verify(
            "I went to the market",
            listOf(correction("went", "Went"))
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `ignores case and punctuation when matching`() {
        val result = CorrectionVerifier.verify(
            "Yesterday, I GO to the market.",
            listOf(correction("i go", "I went"))
        )
        assertEquals(1, result.size)
    }

    @Test
    fun `requires the fragment to be contiguous`() {
        // "I market" appears only as scattered words, not as a phrase, so a
        // correction quoting it is not describing something that was said.
        val result = CorrectionVerifier.verify(
            "I went to the market",
            listOf(correction("I market", "I go to market"))
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `drops corrections with an empty explanation`() {
        val result = CorrectionVerifier.verify(
            "Yesterday I go home",
            listOf(correction("I go", "I went", why = "   "))
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `deduplicates the same change reported twice`() {
        val result = CorrectionVerifier.verify(
            "Yesterday I go home",
            listOf(
                correction("I go", "I went"),
                correction("i  go", "I went", why = "Different wording, same fix.")
            )
        )
        assertEquals(1, result.size)
    }

    @Test
    fun `rejects an implausibly long fragment`() {
        val sentence = (1..30).joinToString(" ") { "word$it" }
        val result = CorrectionVerifier.verify(
            sentence,
            listOf(correction(sentence, "something else"))
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `empty transcript yields nothing`() {
        assertTrue(CorrectionVerifier.verify("", listOf(correction("a", "b"))).isEmpty())
    }
}
