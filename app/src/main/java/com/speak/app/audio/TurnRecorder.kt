package com.speak.app.audio

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformWhile

/**
 * One tap, one spoken turn.
 *
 * Combines the microphone with [VoiceActivityDetector] so the learner taps once,
 * speaks, and stops speaking. Having to tap again to finish breaks the illusion
 * of a conversation, which is the thing the app is trying to create.
 */
class TurnRecorder(
    private val recorder: AudioRecorder = AudioRecorder(),
    private val detectorFactory: () -> VoiceActivityDetector = {
        VoiceActivityDetector(sampleRate = AudioRecorder.SAMPLE_RATE)
    }
) {
    sealed interface Event {
        /** Learning the room's noise floor. Lasts a fraction of a second. */
        data object Calibrating : Event

        /** Ready, waiting for the first word. */
        data object Listening : Event

        /** Speech detected; [level] is 0f..1f for the level meter. */
        data class Speaking(val level: Float) : Event

        /** The turn completed normally. */
        class Captured(val audio: FloatArray, val speechDurationMs: Int) : Event

        /** Nobody spoke. */
        data object NoSpeech : Event
    }

    /**
     * Records until the speaker stops, then completes.
     *
     * [transformWhile] rather than `collect` is what actually releases the
     * microphone: returning false from it cancels the upstream flow immediately,
     * whereas returning early from a `collect` lambda would leave `AudioRecord`
     * running until the caller cancelled the whole coroutine.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun record(): Flow<Event> {
        val detector = detectorFactory()
        // Whisper needs the complete utterance, so frames are accumulated as they
        // arrive rather than being streamed into the model.
        val captured = ArrayList<FloatArray>(256)
        var totalSamples = 0
        var announcedListening = false

        return recorder.frames()
            .transformWhile { frame ->
                captured += frame
                totalSamples += frame.size

                when (detector.accept(frame)) {
                    VoiceActivityDetector.State.CALIBRATING -> true

                    VoiceActivityDetector.State.WAITING -> {
                        if (!announcedListening) {
                            announcedListening = true
                            emit(Event.Listening)
                        }
                        true
                    }

                    VoiceActivityDetector.State.SPEAKING -> {
                        emit(Event.Speaking(detector.normalisedLevel))
                        true
                    }

                    VoiceActivityDetector.State.FINISHED -> {
                        emit(Event.Captured(flatten(captured, totalSamples), detector.speechDurationMs()))
                        false
                    }

                    VoiceActivityDetector.State.TIMED_OUT -> {
                        emit(Event.NoSpeech)
                        false
                    }
                }
            }
            .onStart { emit(Event.Calibrating) }
    }

    private fun flatten(frames: List<FloatArray>, total: Int): FloatArray {
        val out = FloatArray(total)
        var offset = 0
        for (frame in frames) {
            frame.copyInto(out, offset)
            offset += frame.size
        }
        return out
    }
}
