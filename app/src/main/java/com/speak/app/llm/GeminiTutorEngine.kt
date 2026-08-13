package com.speak.app.llm

import com.speak.app.domain.correction.CorrectionVerifier
import com.speak.app.domain.correction.RuleBasedChecker
import com.speak.app.domain.correction.TutorResponseParser
import com.speak.app.domain.model.TutorFeedback
import com.speak.app.domain.tutor.SentenceChunker
import com.speak.app.domain.tutor.TutorEngine
import com.speak.app.domain.tutor.TutorEvent
import com.speak.app.domain.tutor.TutorRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Optional higher-quality feedback through the free tier of the Google AI Studio
 * API.
 *
 * This is strictly an extra. The app is fully usable having never entered a key,
 * no key is ever bundled, and every failure here is reported in a way that lets
 * the caller fall straight back to the on-device model. If the phone is offline,
 * the key is wrong, or the free quota is spent, the learner loses nothing but a
 * little polish.
 */
class GeminiTutorEngine(
    private val apiKeyProvider: () -> String?,
    private val modelId: String = DEFAULT_MODEL,
    private val connectivity: () -> Boolean
) : TutorEngine {

    private val cancelled = AtomicBoolean(false)
    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    override val displayName: String = "Gemini ($modelId)"

    override suspend fun isReady(): Boolean =
        !apiKeyProvider().isNullOrBlank() && connectivity()

    override fun respond(request: TutorRequest): Flow<TutorEvent> = flow {
        emit(TutorEvent.Started)
        cancelled.set(false)

        val key = apiKeyProvider()
        if (key.isNullOrBlank()) {
            emit(TutorEvent.Failed("No API key set."))
            return@flow
        }
        if (!connectivity()) {
            emit(TutorEvent.Failed("No internet connection."))
            return@flow
        }

        val body = requestBody(request)
        val raw = try {
            post(key, body)
        } catch (error: IOException) {
            emit(TutorEvent.Failed(error.message ?: "Could not reach Gemini."))
            return@flow
        }
        if (cancelled.get()) return@flow

        val text = extractText(raw)
        if (text.isNullOrBlank()) {
            emit(TutorEvent.Failed("Gemini returned an empty answer."))
            return@flow
        }

        val parsed = TutorResponseParser.parse(text, request.transcript)
        val merged = CorrectionVerifier.verify(
            request.transcript,
            parsed.corrections + RuleBasedChecker.check(request.transcript)
        )
        val reply = parsed.spokenReply.ifBlank { "Tell me a little more about that." }

        // Split for speech so the online path behaves like the offline one.
        val chunker = SentenceChunker()
        for (sentence in chunker.accept(reply)) emit(TutorEvent.Speakable(sentence))
        chunker.flush().takeIf { it.isNotBlank() }?.let { emit(TutorEvent.Speakable(it)) }
        emit(TutorEvent.ReplyDelta(reply))

        emit(
            TutorEvent.Complete(
                TutorFeedback(
                    transcript = request.transcript,
                    correctedSentence = if (merged.isEmpty()) null else parsed.correctedSentence,
                    corrections = merged,
                    spokenReply = reply
                )
            )
        )
    }.flowOn(Dispatchers.IO)

    private fun requestBody(request: TutorRequest): String {
        val instructions = buildString {
            append("You are a warm, patient English speaking tutor. ")
            append("Your student is an adult Indian English speaker practising spoken conversation.\n")
            append("Return JSON with keys \"reply\", \"corrected\" and \"corrections\".\n")
            append("\"reply\": one or two warm sentences reacting to what the student said, then one question to keep them talking. Never mention grammar there.\n")
            append("\"corrected\": their sentence written correctly, or copied unchanged if it was already correct.\n")
            append("\"corrections\": an array of {\"wrong\", \"right\", \"why\", \"type\"}. ")
            append("Quote \"wrong\" exactly as the student said it. \"why\" is one plain sentence with no grammar jargon. ")
            append("\"type\" is one of tense, article, preposition, plural, word_order, word_choice, other.\n")
            append("Never invent a mistake. If the sentence is correct, \"corrections\" must be [].\n")
            if (!request.topic.isNullOrBlank()) append("Topic: ${request.topic}\n")
            append("Student said: \"${request.transcript}\"")
        }

        val payload = buildJsonObject {
            putJsonArray("contents") {
                for (exchange in request.history.takeLast(3)) {
                    add(turn("user", "Student said: \"${exchange.student}\""))
                    add(turn("model", exchange.tutor))
                }
                add(turn("user", instructions))
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                put("temperature", 0.6)
                put("maxOutputTokens", 800)
            }
        }
        return payload.toString()
    }

    private fun turn(role: String, text: String): JsonObject = buildJsonObject {
        put("role", role)
        putJsonArray("parts") {
            add(buildJsonObject { put("text", text) })
        }
    }

    private fun post(key: String, body: String): String {
        val url = URI("$ENDPOINT/$modelId:generateContent").toURL()
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            // Header rather than a query parameter, so the key never lands in a log.
            connection.setRequestProperty("x-goog-api-key", key)
            connection.outputStream.use { it.write(body.toByteArray()) }

            val code = connection.responseCode
            if (code != 200) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException(friendlyError(code, detail))
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun friendlyError(code: Int, detail: String): String = when (code) {
        400 -> "Gemini rejected the request. Check the key in Settings."
        401, 403 -> "That API key was refused. Paste it again in Settings."
        429 -> "You have reached today's free Gemini limit. The on-device tutor still works."
        in 500..599 -> "Gemini is having trouble. Falling back to the on-device tutor."
        else -> "Gemini error $code." + if (detail.isNotBlank()) " " else ""
    }

    private fun extractText(raw: String): String? {
        val root = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
        val candidates = root["candidates"] as? JsonArray ?: return null
        val first = candidates.firstOrNull() as? JsonObject ?: return null
        val content = first["content"] as? JsonObject ?: return null
        val parts = content["parts"] as? JsonArray ?: return null
        return parts.asSequence()
            .mapNotNull { (it as? JsonObject)?.get("text") as? JsonPrimitive }
            .joinToString("") { it.content }
            .takeIf { it.isNotBlank() }
    }

    override fun cancel() {
        cancelled.set(true)
    }

    companion object {
        private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"

        /** Overridable in Settings, because model names change over time. */
        const val DEFAULT_MODEL = "gemini-2.5-flash"
    }
}
