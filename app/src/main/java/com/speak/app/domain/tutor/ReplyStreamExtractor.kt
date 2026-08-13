package com.speak.app.domain.tutor

/**
 * Pulls the `reply` field out of a JSON document while it is still being
 * generated, character by character.
 *
 * This is what makes the tutor feel like it is talking rather than thinking. The
 * model emits roughly ten tokens a second, so waiting for the closing brace
 * before speaking costs ten to twenty seconds of silence. Because the grammar
 * puts `reply` first, its text is complete long before the corrections are, and
 * this extractor hands it over as it arrives so speech can begin almost
 * immediately.
 *
 * Only the `reply` value is extracted; everything else is ignored and left to the
 * full parse at the end of the turn.
 */
class ReplyStreamExtractor {

    private enum class Phase { SEEKING_KEY, AFTER_KEY, IN_VALUE, DONE }

    private var phase = Phase.SEEKING_KEY
    private val window = StringBuilder()
    private var escaped = false

    /** True once the reply string has been closed. */
    val isComplete: Boolean get() = phase == Phase.DONE

    /**
     * Feeds newly generated text.
     *
     * @return any reply text that became available, decoded, possibly empty.
     */
    fun accept(delta: String): String {
        if (phase == Phase.DONE || delta.isEmpty()) return ""
        val out = StringBuilder()

        for (ch in delta) {
            when (phase) {
                Phase.SEEKING_KEY -> {
                    window.append(ch)
                    if (window.length > KEY_WINDOW) window.deleteRange(0, window.length - KEY_WINDOW)
                    if (window.contains("\"reply\"")) {
                        phase = Phase.AFTER_KEY
                        window.clear()
                    }
                }

                Phase.AFTER_KEY -> {
                    // Skip the colon and any whitespace, then the opening quote.
                    if (ch == '"') phase = Phase.IN_VALUE
                }

                Phase.IN_VALUE -> {
                    when {
                        escaped -> {
                            out.append(unescape(ch))
                            escaped = false
                        }
                        ch == '\\' -> escaped = true
                        ch == '"' -> phase = Phase.DONE
                        else -> out.append(ch)
                    }
                }

                Phase.DONE -> Unit
            }
            if (phase == Phase.DONE) break
        }
        return out.toString()
    }

    private fun unescape(ch: Char): Char = when (ch) {
        'n' -> '\n'
        't' -> '\t'
        'r' -> '\r'
        'b' -> '\b'
        // A \\uXXXX sequence would need four more characters; the tutor's replies
        // are plain English, so dropping the marker is safer than mis-decoding.
        'u' -> ' '
        else -> ch
    }

    fun reset() {
        phase = Phase.SEEKING_KEY
        window.clear()
        escaped = false
    }

    private companion object {
        /** Long enough to span the key even if it is split across tokens. */
        const val KEY_WINDOW = 16
    }
}
