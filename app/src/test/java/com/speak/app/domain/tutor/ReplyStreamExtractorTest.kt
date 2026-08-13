package com.speak.app.domain.tutor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The streaming extractor is what lets speech start before the model has finished
 * thinking, so it has to survive the JSON arriving in arbitrarily ugly fragments.
 */
class ReplyStreamExtractorTest {

    /** Feeds text one character at a time, the worst case for a state machine. */
    private fun drip(raw: String): String {
        val extractor = ReplyStreamExtractor()
        return buildString { raw.forEach { append(extractor.accept(it.toString())) } }
    }

    @Test
    fun `extracts the reply from a complete document`() {
        val raw = """{"reply": "Good morning. What did you eat?", "corrected": "", "corrections": []}"""
        assertEquals("Good morning. What did you eat?", drip(raw))
    }

    @Test
    fun `emits text before the document is finished`() {
        // This is the whole point: usable text while corrections are still coming.
        val extractor = ReplyStreamExtractor()
        val first = extractor.accept("""{"reply": "That sounds good.""")
        assertEquals("That sounds good.", first)
        assertFalse(extractor.isComplete)
    }

    @Test
    fun `marks itself complete at the closing quote`() {
        val extractor = ReplyStreamExtractor()
        extractor.accept("""{"reply": "Done.""")
        assertFalse(extractor.isComplete)
        extractor.accept("\", \"corrected\"")
        assertTrue(extractor.isComplete)
    }

    @Test
    fun `handles the key split across fragments`() {
        val extractor = ReplyStreamExtractor()
        val out = buildString {
            append(extractor.accept("""{"re"""))
            append(extractor.accept("""ply""""))
            append(extractor.accept(""": "Hello."""))
        }
        assertEquals("Hello.", out)
    }

    @Test
    fun `unescapes escaped quotes inside the reply`() {
        val raw = """{"reply": "Say \"thank you\" next time.", "corrected": ""}"""
        assertEquals("Say \"thank you\" next time.", drip(raw))
    }

    @Test
    fun `unescapes newlines`() {
        val raw = """{"reply": "First.\nSecond.", "corrected": ""}"""
        assertEquals("First.\nSecond.", drip(raw))
    }

    @Test
    fun `an escaped backslash does not end the string`() {
        val raw = """{"reply": "back\\slash", "corrected": ""}"""
        assertEquals("back\\slash", drip(raw))
    }

    @Test
    fun `ignores content after the reply closes`() {
        val raw = """{"reply": "Only this.", "corrected": "not this", "corrections": []}"""
        assertEquals("Only this.", drip(raw))
    }

    @Test
    fun `yields nothing when there is no reply key`() {
        assertEquals("", drip("""{"corrected": "x", "corrections": []}"""))
    }

    @Test
    fun `reset allows reuse across turns`() {
        val extractor = ReplyStreamExtractor()
        extractor.accept("""{"reply": "One."""")
        assertTrue(extractor.isComplete)
        extractor.reset()
        assertFalse(extractor.isComplete)
        assertEquals("Two.", extractor.accept("""{"reply": "Two."""))
    }
}
