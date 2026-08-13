package com.speak.app.domain.correction

import com.speak.app.domain.model.MistakeCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorResponseParserTest {

    @Test
    fun `parses a well formed response`() {
        val raw = """
            {"reply": "Nice. What did you buy?",
             "corrected": "Yesterday I went to the market.",
             "corrections": [
               {"wrong": "I go", "right": "I went", "why": "It happened yesterday.", "type": "tense"}
             ]}
        """.trimIndent()

        val feedback = TutorResponseParser.parse(raw, "Yesterday I go to the market")

        assertEquals("Nice. What did you buy?", feedback.spokenReply)
        assertEquals("Yesterday I went to the market.", feedback.correctedSentence)
        assertEquals(1, feedback.corrections.size)
        assertEquals(MistakeCategory.TENSE, feedback.corrections.first().category)
    }

    @Test
    fun `strips markdown fences and surrounding prose`() {
        val raw = """
            Sure! Here is the feedback:
            ```json
            {"reply": "Good.", "corrected": "I am fine.", "corrections": []}
            ```
        """.trimIndent()

        val feedback = TutorResponseParser.parse(raw, "I am fine")
        assertEquals("Good.", feedback.spokenReply)
        assertTrue(feedback.corrections.isEmpty())
    }

    @Test
    fun `braces inside a string do not end the object early`() {
        // A naive indexOf/lastIndexOf pair breaks on this input.
        val raw = """{"reply": "Use {this} form.", "corrected": "", "corrections": []}"""
        val feedback = TutorResponseParser.parse(raw, "something")
        assertEquals("Use {this} form.", feedback.spokenReply)
    }

    @Test
    fun `escaped quotes inside a string are handled`() {
        val raw = """{"reply": "Say \"hello\" first.", "corrected": "", "corrections": []}"""
        val feedback = TutorResponseParser.parse(raw, "something")
        assertEquals("Say \"hello\" first.", feedback.spokenReply)
    }

    @Test
    fun `unparseable output still yields a usable reply`() {
        // Failing safe means the conversation continues and no mistakes are claimed.
        val feedback = TutorResponseParser.parse("I think that was good!", "I am fine")
        assertEquals("I think that was good!", feedback.spokenReply)
        assertTrue(feedback.corrections.isEmpty())
        assertNull(feedback.correctedSentence)
    }

    @Test
    fun `hallucinated corrections are discarded during parsing`() {
        val raw = """
            {"reply": "Ok.", "corrected": "I have two brothers.",
             "corrections": [{"wrong": "I has", "right": "I have", "why": "x", "type": "tense"}]}
        """.trimIndent()

        // The learner never said "I has", so nothing survives and the corrected
        // sentence is dropped along with it.
        val feedback = TutorResponseParser.parse(raw, "I have two brothers")
        assertTrue(feedback.corrections.isEmpty())
        assertNull(feedback.correctedSentence)
        assertTrue(feedback.wasCorrect)
    }

    @Test
    fun `accepts alternative field names`() {
        val raw = """{"say": "Fine.", "corrected": "", "corrections":
            [{"was": "I go", "fix": "I went", "explanation": "Past.", "category": "verb tense"}]}"""
        val feedback = TutorResponseParser.parse(raw, "Yesterday I go there")
        assertEquals("Fine.", feedback.spokenReply)
        assertEquals(1, feedback.corrections.size)
        assertEquals(MistakeCategory.TENSE, feedback.corrections.first().category)
    }

    @Test
    fun `extracts the first balanced object`() {
        val found = TutorResponseParser.extractJsonObject("noise {\"a\": {\"b\": 1}} trailing")
        assertNotNull(found)
        assertEquals("{\"a\": {\"b\": 1}}", found)
    }

    @Test
    fun `unknown category falls back to other`() {
        assertEquals(MistakeCategory.OTHER, MistakeCategory.from("gerundive mood"))
        assertEquals(MistakeCategory.PREPOSITION, MistakeCategory.from("preposition"))
        assertEquals(MistakeCategory.WORD_ORDER, MistakeCategory.from("word_order"))
        assertEquals(MistakeCategory.OTHER, MistakeCategory.from(null))
    }
}
