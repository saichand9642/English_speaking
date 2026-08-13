package com.speak.app.domain.correction

import com.speak.app.domain.model.DiffSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiffAlignerTest {

    private fun List<DiffSpan>.render(): String = joinToString(" ") { span ->
        when (span.kind) {
            DiffSpan.Kind.UNCHANGED -> span.text
            DiffSpan.Kind.REMOVED -> "[-${span.text}]"
            DiffSpan.Kind.ADDED -> "[+${span.text}]"
        }
    }

    @Test
    fun `marks a replaced word`() {
        val spans = DiffAligner.align("Yesterday I go home", "Yesterday I went home")
        assertEquals("Yesterday I [-go] [+went] home", spans.render())
    }

    @Test
    fun `marks an inserted word`() {
        val spans = DiffAligner.align("I went to market", "I went to the market")
        assertEquals("I went to [+the] market", spans.render())
    }

    @Test
    fun `marks a deleted word`() {
        val spans = DiffAligner.align("We discussed about it", "We discussed it")
        assertEquals("We discussed [-about] it", spans.render())
    }

    @Test
    fun `identical sentences produce no changes`() {
        val spans = DiffAligner.align("I am fine", "I am fine")
        assertTrue(spans.all { it.kind == DiffSpan.Kind.UNCHANGED })
    }

    @Test
    fun `differences in case alone are not changes`() {
        // Whisper's capitalisation is its own; flagging it as the learner's mistake
        // would be noise.
        val spans = DiffAligner.align("yesterday i went home", "Yesterday I went home")
        assertTrue(spans.all { it.kind == DiffSpan.Kind.UNCHANGED })
    }

    @Test
    fun `keeps the corrected spelling for shared words`() {
        val spans = DiffAligner.align("i am fine", "I am fine")
        assertEquals("I am fine", spans.render())
    }

    @Test
    fun `merges adjacent changes of the same kind`() {
        val spans = DiffAligner.align("He is having two brother", "He has two brothers")
        // Runs collapse so the UI draws one strike-through, not several.
        val removed = spans.filter { it.kind == DiffSpan.Kind.REMOVED }
        assertTrue(removed.none { it.text.isEmpty() })
        assertEquals(spans, spans.distinct())
    }

    @Test
    fun `handles a full rewrite`() {
        val spans = DiffAligner.align("me go there", "I went there")
        assertTrue(spans.any { it.kind == DiffSpan.Kind.REMOVED })
        assertTrue(spans.any { it.kind == DiffSpan.Kind.ADDED })
        assertTrue(spans.any { it.kind == DiffSpan.Kind.UNCHANGED && it.text == "there" })
    }

    @Test
    fun `empty original is all additions`() {
        val spans = DiffAligner.align("", "I went home")
        assertEquals(1, spans.size)
        assertEquals(DiffSpan.Kind.ADDED, spans.first().kind)
    }

    @Test
    fun `empty correction is all removals`() {
        val spans = DiffAligner.align("I went home", "")
        assertEquals(1, spans.size)
        assertEquals(DiffSpan.Kind.REMOVED, spans.first().kind)
    }

    @Test
    fun `both empty yields nothing`() {
        assertTrue(DiffAligner.align("", "").isEmpty())
    }

    @Test
    fun `collapses extra whitespace`() {
        val spans = DiffAligner.align("I   went    home", "I went home")
        assertTrue(spans.all { it.kind == DiffSpan.Kind.UNCHANGED })
    }
}
