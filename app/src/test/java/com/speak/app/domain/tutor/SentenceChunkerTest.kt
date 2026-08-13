package com.speak.app.domain.tutor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceChunkerTest {

    @Test
    fun `releases a sentence once it is complete`() {
        val chunker = SentenceChunker()
        assertTrue(chunker.accept("That sounds").isEmpty())
        assertEquals(listOf("That sounds good."), chunker.accept(" good. And then"))
    }

    @Test
    fun `handles several sentences in one fragment`() {
        val chunker = SentenceChunker()
        val out = chunker.accept("One. Two! Three? ")
        assertEquals(listOf("One.", "Two!", "Three?"), out)
    }

    @Test
    fun `keeps decimals and abbreviations intact`() {
        // A full stop only ends a sentence when a space or nothing follows it,
        // otherwise text-to-speech would break "3.5" into two utterances.
        val chunker = SentenceChunker()
        assertTrue(chunker.accept("It costs 3.5 rupees").isEmpty())
        assertEquals(listOf("It costs 3.5 rupees."), chunker.accept(". Next"))
    }

    @Test
    fun `treats question and exclamation marks as boundaries`() {
        val chunker = SentenceChunker()
        assertEquals(listOf("Really?"), chunker.accept("Really? Yes"))
    }

    @Test
    fun `breaks a very long run at a word boundary`() {
        // Without this, a model that never emits punctuation would leave speech
        // waiting for a full stop that is not coming.
        val chunker = SentenceChunker(softLimit = 40)
        val long = "word ".repeat(20)
        val out = chunker.accept(long)
        assertTrue(out.isNotEmpty())
        // A word is never split in half.
        assertTrue(out.all { chunk -> chunk.split(" ").all { it == "word" || it.isEmpty() } })
    }

    @Test
    fun `flush returns the trailing fragment`() {
        val chunker = SentenceChunker()
        chunker.accept("An unfinished thought")
        assertEquals("An unfinished thought", chunker.flush())
        assertEquals("", chunker.flush())
    }

    @Test
    fun `a sentence ending at a closing quote is released`() {
        val chunker = SentenceChunker()
        assertEquals(listOf("He said \"hello.\""), chunker.accept("He said \"hello.\" Then"))
    }

    @Test
    fun `empty input yields nothing`() {
        val chunker = SentenceChunker()
        assertTrue(chunker.accept("").isEmpty())
    }

    @Test
    fun `newline ends a sentence`() {
        val chunker = SentenceChunker()
        assertEquals(listOf("A line"), chunker.accept("A line\nmore"))
    }
}
