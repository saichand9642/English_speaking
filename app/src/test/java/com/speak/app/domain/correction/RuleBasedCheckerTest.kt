package com.speak.app.domain.correction

import com.speak.app.domain.model.MistakeCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The deterministic checks exist to catch the frequent Indian-English patterns a
 * 1B model misses. Every rule must produce a real fix and, just as importantly,
 * must stay silent on correct sentences.
 */
class RuleBasedCheckerTest {

    private fun firstFix(sentence: String): String? =
        RuleBasedChecker.check(sentence).firstOrNull()?.right

    @Test
    fun `discuss about`() {
        val found = RuleBasedChecker.check("We discussed about the plan")
        assertEquals(1, found.size)
        assertEquals("discussed", found.first().right)
        assertEquals(MistakeCategory.PREPOSITION, found.first().category)
    }

    @Test
    fun `revert back`() {
        assertEquals("return", firstFix("Please return back the book"))
    }

    @Test
    fun `since with a duration`() {
        val found = RuleBasedChecker.check("I am working here since 3 years")
        assertTrue(found.any { it.right.equals("for 3 years", ignoreCase = true) })
    }

    @Test
    fun `one of my friend`() {
        assertEquals("one of my friends", firstFix("one of my friend came"))
    }

    @Test
    fun `uncountable plural`() {
        assertEquals("information", firstFix("He gave me many informations"))
    }

    @Test
    fun `did plus past tense`() {
        val found = RuleBasedChecker.check("I didn't went there")
        assertEquals(1, found.size)
        assertEquals("didn't go", found.first().right)
        assertEquals(MistakeCategory.TENSE, found.first().category)
    }

    @Test
    fun `double comparative`() {
        assertEquals("better", firstFix("This one is more better"))
    }

    @Test
    fun `stative having with a possession`() {
        assertEquals("He has two brothers", firstFix("He is having two brothers"))
        assertEquals("I have a car", firstFix("I am having a car"))
    }

    @Test
    fun `progressive having with a meal is left alone`() {
        // "I am having lunch" is correct English. Flagging it would be exactly the
        // invented mistake this app promises never to make.
        assertTrue(RuleBasedChecker.check("I am having lunch right now").isEmpty())
        assertTrue(RuleBasedChecker.check("We are having a party tomorrow").isEmpty())
        assertTrue(RuleBasedChecker.check("I am having trouble with my laptop").isEmpty())
    }

    @Test
    fun `stative knowing`() {
        assertEquals("I know", firstFix("I am knowing the answer"))
    }

    @Test
    fun `question word order`() {
        val found = RuleBasedChecker.check("Where you are going?")
        assertEquals(1, found.size)
        assertEquals("Where are you", found.first().right)
        assertEquals(MistakeCategory.WORD_ORDER, found.first().category)
    }

    @Test
    fun `cousin brother`() {
        assertEquals("cousin", firstFix("My cousin brother is here"))
    }

    @Test
    fun `good name`() {
        assertEquals("your name", firstFix("What is your good name?"))
    }

    @Test
    fun `out of station`() {
        assertEquals("out of town", firstFix("I am out of station this week"))
    }

    @Test
    fun `prepone`() {
        assertTrue(RuleBasedChecker.check("Can we prepone the meeting?").isNotEmpty())
    }

    @Test
    fun `morning time`() {
        assertEquals("morning", firstFix("I go in the morning time"))
    }

    // ---- the important negative cases ----

    @Test
    fun `correct sentences are left alone`() {
        val clean = listOf(
            "Yesterday I went to the market and bought some rice.",
            "I have been working here for three years.",
            "He has two brothers and one sister.",
            "Where are you going tomorrow?",
            "I did not go to the office yesterday.",
            "This option is better than the other one.",
            "We discussed the plan this morning.",
            "One of my friends lives in Delhi.",
            "She gave me some useful information.",
            "I am having lunch right now."
        )
        clean.forEach { sentence ->
            assertTrue(
                "false positive on: $sentence -> ${RuleBasedChecker.check(sentence)}",
                RuleBasedChecker.check(sentence).isEmpty()
            )
        }
    }

    @Test
    fun `empty input yields nothing`() {
        assertTrue(RuleBasedChecker.check("").isEmpty())
        assertTrue(RuleBasedChecker.check("   ").isEmpty())
    }

    @Test
    fun `two rules never claim the same words`() {
        val found = RuleBasedChecker.check("I didn't went and we discussed about it")
        assertEquals(2, found.size)
    }

    @Test
    fun `applyAll rewrites the whole sentence`() {
        val fixed = RuleBasedChecker.applyAll("We discussed about one of my friend")
        assertTrue(fixed.contains("discussed one of my friends"))
    }

    @Test
    fun `applyAll leaves a correct sentence untouched`() {
        val sentence = "I went to the market yesterday."
        assertEquals(sentence, RuleBasedChecker.applyAll(sentence))
    }

    @Test
    fun `every rule supplies an explanation and a real change`() {
        val samples = listOf(
            "We discussed about it", "Please return back", "since 5 years",
            "one of my friend", "many informations", "I didn't went",
            "more better", "He is having two cars", "I am knowing it",
            "Where you are going", "my cousin brother", "your good name",
            "out of station", "prepone it", "in the evening time"
        )
        samples.forEach { sentence ->
            RuleBasedChecker.check(sentence).forEach { correction ->
                assertTrue("blank why on: $sentence", correction.explanation.isNotBlank())
                assertTrue(
                    "no-op fix on: $sentence",
                    !correction.wrong.equals(correction.right, ignoreCase = true)
                )
            }
        }
    }
}
