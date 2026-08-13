package com.speak.app.llm

/**
 * Builds the prompt for the on-device tutor, in Gemma 3's turn format.
 *
 * Gemma has no system role, so the instructions live in the first user turn. One
 * worked example follows as a user/model pair. A single example roughly doubles
 * the reliability of a 1B model's output shape, and two barely improve on one, so
 * one is what we pay for -- every extra token here is prefill time the learner
 * waits through on a CPU-only phone.
 */
internal object TutorPrompt {

    data class Exchange(val student: String, val tutor: String)

    /** Turns kept from earlier in the conversation. Kept small to bound prefill. */
    private const val HISTORY_TURNS = 2

    private const val INSTRUCTIONS = """You are a warm, patient English speaking tutor. Your student is an adult Indian English speaker practising spoken conversation.

Answer with JSON only, in this exact shape:
{"reply": "...", "corrected": "...", "corrections": [{"wrong": "...", "right": "...", "why": "...", "type": "..."}]}

Rules:
- "reply": one or two warm sentences reacting to what the student said, then one question to keep them talking. Never mention mistakes or grammar here.
- "corrected": the student's sentence written correctly. If it was already correct, copy it unchanged.
- "corrections": one entry for each real mistake. Copy "wrong" exactly as the student said it. "why" is one short sentence in plain words, with no grammar jargon. "type" is one of: tense, article, preposition, plural, word_order, word_choice, other.
- If the sentence has no mistakes, "corrections" must be [].
- Never invent a mistake. Only quote words the student actually said."""

    private const val EXAMPLE_STUDENT =
        """Topic: weekends
Student said: "Yesterday I go to market and buy two kilo rice.""""

    private const val EXAMPLE_MODEL =
        """{"reply": "That sounds like a useful trip to the market. What did you cook with the rice?", "corrected": "Yesterday I went to the market and bought two kilos of rice.", "corrections": [{"wrong": "I go", "right": "I went", "why": "This happened yesterday, so the verb changes to its past form.", "type": "tense"}, {"wrong": "buy", "right": "bought", "why": "The second action was also yesterday, so it needs the past form too.", "type": "tense"}, {"wrong": "two kilo", "right": "two kilos of", "why": "More than one kilo takes an s on the end.", "type": "plural"}]}"""

    fun build(topic: String?, history: List<Exchange>, studentSentence: String): String =
        buildString {
            append("<start_of_turn>user\n")
            append(INSTRUCTIONS)
            append("\n\n")
            append(EXAMPLE_STUDENT)
            append("<end_of_turn>\n")
            append("<start_of_turn>model\n")
            append(EXAMPLE_MODEL)
            append("<end_of_turn>\n")

            for (exchange in history.takeLast(HISTORY_TURNS)) {
                append("<start_of_turn>user\n")
                append("Student said: \"")
                append(exchange.student.trim().take(300))
                append("\"<end_of_turn>\n")
                append("<start_of_turn>model\n")
                // Only the spoken half of an earlier turn is worth replaying: the
                // corrections were already delivered and would waste prefill.
                append("{\"reply\": \"")
                append(escape(exchange.tutor.trim().take(300)))
                append("\", \"corrected\": \"\", \"corrections\": []}")
                append("<end_of_turn>\n")
            }

            append("<start_of_turn>user\n")
            if (!topic.isNullOrBlank()) {
                append("Topic: ")
                append(topic.trim())
                append("\n")
            }
            append("Student said: \"")
            append(studentSentence.trim().take(500))
            append("\"<end_of_turn>\n")
            append("<start_of_turn>model\n")
        }

    /** Minimal JSON string escaping for text embedded in the replayed history. */
    private fun escape(text: String): String =
        text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
}
