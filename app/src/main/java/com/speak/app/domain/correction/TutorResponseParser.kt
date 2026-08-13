package com.speak.app.domain.correction

import com.speak.app.domain.model.Correction
import com.speak.app.domain.model.MistakeCategory
import com.speak.app.domain.model.TutorFeedback
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Turns the tutor model's raw output into a [TutorFeedback].
 *
 * The on-device model is constrained by a GBNF grammar, so its JSON should
 * always be well formed. This parser is still deliberately forgiving, because
 * the optional Gemini path is not grammar-constrained and small models like to
 * wrap JSON in prose or markdown fences. Parsing must never be the reason a turn
 * fails: if the JSON is unusable we fall back to treating the whole output as
 * spoken reply with no corrections, which is the safe direction to fail in --
 * the app stays conversational and simply claims no mistakes.
 */
object TutorResponseParser {

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        allowTrailingComma = true
    }

    fun parse(raw: String, transcript: String): TutorFeedback {
        val body = extractJsonObject(raw)
            ?: return TutorFeedback(
                transcript = transcript,
                correctedSentence = null,
                corrections = emptyList(),
                spokenReply = cleanProse(raw)
            )

        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?: return TutorFeedback(
                transcript = transcript,
                correctedSentence = null,
                corrections = emptyList(),
                spokenReply = cleanProse(raw)
            )

        val corrections = (root["corrections"] as? JsonArray).orEmpty()
            .mapNotNull { element -> (element as? JsonObject)?.toCorrection() }

        val corrected = root.string("corrected")?.takeIf { it.isNotBlank() }
        val reply = root.string("reply")?.takeIf { it.isNotBlank() }
            ?: root.string("say")?.takeIf { it.isNotBlank() }
            ?: ""

        // Everything the model claims is then checked against the real transcript.
        val verified = CorrectionVerifier.verify(transcript, corrections)

        return TutorFeedback(
            transcript = transcript,
            // A "corrected sentence" is meaningless if nothing survived verification.
            correctedSentence = if (verified.isEmpty()) null else corrected,
            corrections = verified,
            spokenReply = reply
        )
    }

    private fun JsonObject.toCorrection(): Correction? {
        val wrong = string("wrong") ?: string("was") ?: return null
        val right = string("right") ?: string("fix") ?: return null
        val why = string("why") ?: string("explanation") ?: ""
        val type = string("type") ?: string("category")
        if (wrong.isBlank() || right.isBlank()) return null
        return Correction(
            wrong = wrong.trim(),
            right = right.trim(),
            explanation = why.trim(),
            category = MistakeCategory.from(type)
        )
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNullSafe()

    private fun JsonPrimitive.contentOrNullSafe(): String? =
        if (isString || content != "null") content else null

    private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> =
        this ?: emptyList()

    /**
     * Finds the first balanced JSON object in the text, ignoring braces that
     * appear inside string literals. A plain indexOf/lastIndexOf pair breaks as
     * soon as an explanation contains a brace.
     */
    internal fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until raw.length) {
            val ch = raw[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == '"' -> inString = false
                }
                continue
            }
            when (ch) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return raw.substring(start, index + 1)
                }
            }
        }
        return null
    }

    /** Strips markdown fences and stray JSON so a fallback reply reads as speech. */
    private fun cleanProse(raw: String): String =
        raw.replace(Regex("```[a-zA-Z]*"), "")
            .replace("```", "")
            .trim()
}
