package com.speak.app.llm

import com.speak.app.domain.correction.CorrectionVerifier
import com.speak.app.domain.correction.RuleBasedChecker
import com.speak.app.domain.correction.TutorResponseParser
import com.speak.app.domain.model.Correction
import com.speak.app.domain.model.TutorFeedback
import com.speak.app.domain.tutor.ReplyStreamExtractor
import com.speak.app.domain.tutor.SentenceChunker
import com.speak.app.domain.tutor.TutorEngine
import com.speak.app.domain.tutor.TutorEvent
import com.speak.app.domain.tutor.TutorRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * The offline tutor: Gemma 3 1B, int4, running on the phone's CPU through
 * llama.cpp.
 *
 * Three things here exist specifically to make a 1B model trustworthy enough for
 * this job:
 *
 * 1. Its output shape is fixed by a GBNF grammar, so broken JSON cannot occur.
 * 2. Its spoken reply is streamed out mid-generation, so the learner hears an
 *    answer seconds before the model has finished producing corrections.
 * 3. Every correction it claims is checked against the real transcript and
 *    discarded if the quoted words were never said, then merged with
 *    deterministic rule checks for the errors it reliably misses.
 */
class LocalTutorEngine(
    private val modelPath: String,
    private val contextSize: Int = 1536,
    private val maxReplyTokens: Int = 320,
    private val threads: Int = defaultThreads()
) : TutorEngine {

    private val bridge = LlamaBridge()
    private val mutex = Mutex()

    override val displayName: String = "Gemma 3 1B (on device)"

    val isLoaded: Boolean get() = bridge.isLoaded

    suspend fun load(): Boolean = withContext(Dispatchers.IO) {
        LlamaBridge.ensureLibraryLoaded()
        mutex.withLock { bridge.load(modelPath, contextSize, threads) }
    }

    override suspend fun isReady(): Boolean = bridge.isLoaded

    override fun respond(request: TutorRequest): Flow<TutorEvent> = flow {
        emit(TutorEvent.Started)

        if (!bridge.isLoaded) {
            emit(TutorEvent.Failed("The tutor model is not loaded yet."))
            return@flow
        }
        if (request.transcript.isBlank()) {
            emit(TutorEvent.Failed("I didn't catch that. Try again."))
            return@flow
        }

        mutex.withLock {
            val prompt = TutorPrompt.build(
                topic = request.topic,
                history = request.history.map { TutorPrompt.Exchange(it.student, it.tutor) },
                studentSentence = request.transcript
            )

            val promptTokens = bridge.beginTurn(
                prompt = prompt,
                maxTokens = maxReplyTokens,
                temperature = TEMPERATURE,
                topP = TOP_P,
                topK = TOP_K,
                seed = Random.nextInt(Int.MAX_VALUE),
                grammar = TutorGrammar.GBNF
            )
            if (promptTokens < 0) {
                emit(TutorEvent.Failed("The tutor could not start. Try a shorter sentence."))
                return@flow
            }

            val raw = StringBuilder()
            val extractor = ReplyStreamExtractor()
            val chunker = SentenceChunker()

            while (currentCoroutineContext().isActive) {
                val piece = bridge.nextPiece() ?: break
                raw.append(piece)

                // Surface the spoken reply the moment it exists, before the
                // corrections that follow it in the JSON have been generated.
                val replyText = extractor.accept(piece)
                if (replyText.isNotEmpty()) {
                    emit(TutorEvent.ReplyDelta(replyText))
                    for (sentence in chunker.accept(replyText)) {
                        emit(TutorEvent.Speakable(sentence))
                    }
                }
            }

            if (!currentCoroutineContext().isActive) {
                bridge.cancel()
                return@flow
            }

            chunker.flush().takeIf { it.isNotBlank() }?.let { emit(TutorEvent.Speakable(it)) }

            emit(TutorEvent.Complete(assemble(raw.toString(), request.transcript)))
        }
    }.flowOn(Dispatchers.Default)

    /**
     * Combines the model's verified corrections with the deterministic rule
     * checks, then repairs anything the model left blank.
     */
    private fun assemble(raw: String, transcript: String): TutorFeedback {
        val parsed = TutorResponseParser.parse(raw, transcript)

        // Rule hits come second so a model correction for the same fragment wins,
        // since its explanation is tailored to what was actually said.
        val merged = CorrectionVerifier.verify(
            transcript,
            parsed.corrections + RuleBasedChecker.check(transcript)
        )

        val corrected = when {
            merged.isEmpty() -> null
            !parsed.correctedSentence.isNullOrBlank() -> parsed.correctedSentence
            // The model gave no corrected sentence, so build one from the rules.
            else -> RuleBasedChecker.applyAll(transcript).takeIf { it != transcript }
        }

        val reply = parsed.spokenReply.ifBlank {
            if (merged.isEmpty()) {
                "That was well said. Tell me a little more about it."
            } else {
                "Good try. Let's keep going -- what happened next?"
            }
        }

        return TutorFeedback(
            transcript = transcript,
            correctedSentence = corrected,
            corrections = merged.take(MAX_CORRECTIONS),
            spokenReply = reply
        )
    }

    override fun cancel() = bridge.cancel()

    fun release() = bridge.release()

    fun systemInfo(): String = runCatching {
        LlamaBridge.ensureLibraryLoaded()
        bridge.systemInfo()
    }.getOrElse { "unavailable" }

    companion object {
        /**
         * Low but not zero. Greedy decoding makes the tutor's replies repeat the
         * same few phrasings turn after turn, which reads as robotic; this is warm
         * enough to vary while staying close to the evidence when correcting.
         */
        private const val TEMPERATURE = 0.5f
        private const val TOP_P = 0.9f
        private const val TOP_K = 40

        /** More than this on one spoken sentence is discouraging, not useful. */
        private const val MAX_CORRECTIONS = 4

        fun defaultThreads(): Int =
            Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
    }
}

/** Convenience for tests and callers that only want the merge behaviour. */
internal fun mergeCorrections(
    transcript: String,
    modelCorrections: List<Correction>
): List<Correction> = CorrectionVerifier.verify(
    transcript,
    modelCorrections + RuleBasedChecker.check(transcript)
)
