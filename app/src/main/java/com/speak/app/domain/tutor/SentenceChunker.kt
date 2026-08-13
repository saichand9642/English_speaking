package com.speak.app.domain.tutor

/**
 * Buffers streamed text and releases it one sentence at a time.
 *
 * Text-to-speech sounds wrong when fed word by word -- it loses sentence
 * intonation and the pauses land in the wrong places. Handing it whole sentences
 * keeps the prosody natural while still letting speech start before the model has
 * finished generating.
 */
class SentenceChunker(
    /** Speak a partial sentence once it gets this long, so speech never stalls. */
    private val softLimit: Int = 160
) {
    private val buffer = StringBuilder()

    /** @return sentences that are now complete and ready to speak. */
    fun accept(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        buffer.append(text)

        val ready = mutableListOf<String>()
        while (true) {
            val boundary = findBoundary()
            if (boundary < 0) break
            val sentence = buffer.substring(0, boundary + 1).trim()
            buffer.deleteRange(0, boundary + 1)
            if (sentence.isNotEmpty()) ready += sentence
        }

        if (buffer.length >= softLimit) {
            // Break at the last space so a word is never split in half.
            val breakAt = buffer.lastIndexOf(' ')
            if (breakAt > 0) {
                val chunk = buffer.substring(0, breakAt).trim()
                buffer.deleteRange(0, breakAt + 1)
                if (chunk.isNotEmpty()) ready += chunk
            }
        }
        return ready
    }

    /** Releases whatever is left once generation ends. */
    fun flush(): String {
        val remaining = buffer.toString().trim()
        buffer.clear()
        return remaining
    }

    /**
     * Index of a sentence-ending character, or -1.
     *
     * A full stop only ends a sentence when what follows is a space or nothing --
     * that keeps "3.5" and "Mr. Rao" intact.
     */
    private fun findBoundary(): Int {
        for (index in buffer.indices) {
            val ch = buffer[index]
            // A line break is always a boundary; nothing that follows can change it.
            if (ch == '\n') return index
            if (ch != '.' && ch != '!' && ch != '?') continue

            // A closing quote or bracket after the stop belongs to this sentence,
            // so `He said "hello."` is spoken whole instead of being cut before
            // its own quotation mark.
            var end = index
            while (end + 1 < buffer.length && buffer[end + 1] in CLOSERS) end++

            // Nothing after it yet: wait, in case this turns out to be "3.5".
            val next = buffer.getOrNull(end + 1) ?: return -1
            if (next == ' ' || next == '\n') return end
        }
        return -1
    }

    private companion object {
        val CLOSERS = charArrayOf('"', '\'', ')', ']', '”')
    }
}
