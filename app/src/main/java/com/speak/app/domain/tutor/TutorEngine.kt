package com.speak.app.domain.tutor

import com.speak.app.domain.model.TutorFeedback
import kotlinx.coroutines.flow.Flow

data class TutorExchange(val student: String, val tutor: String)

data class TutorRequest(
    /** Exactly what the learner said, errors intact. */
    val transcript: String,
    val topic: String?,
    val history: List<TutorExchange> = emptyList()
)

sealed interface TutorEvent {
    /** The engine has started work. */
    data object Started : TutorEvent

    /** Reply text for the screen, as it is generated. */
    data class ReplyDelta(val text: String) : TutorEvent

    /** A complete sentence, ready to be spoken aloud immediately. */
    data class Speakable(val sentence: String) : TutorEvent

    /** The turn finished; [feedback] is verified and ready to display. */
    data class Complete(val feedback: TutorFeedback) : TutorEvent

    /** The turn failed. [message] is safe to show the user. */
    data class Failed(val message: String) : TutorEvent
}

/**
 * A source of tutoring feedback.
 *
 * Two implementations exist: the on-device model, which always works, and the
 * optional Gemini path, which is better but requires a key and a connection. The
 * app treats them as interchangeable so that the online one can be absent
 * entirely without any other code noticing.
 */
interface TutorEngine {
    /** Shown in settings, e.g. "Gemma 3 1B (on device)". */
    val displayName: String

    /** Whether this engine can serve a request right now. */
    suspend fun isReady(): Boolean

    fun respond(request: TutorRequest): Flow<TutorEvent>

    /** Abandons the current turn. */
    fun cancel()
}
