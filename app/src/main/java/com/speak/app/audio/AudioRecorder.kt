package com.speak.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Captures 16 kHz mono audio, which is exactly what whisper.cpp wants, so
 * nothing has to be resampled between the microphone and the model.
 *
 * `VOICE_RECOGNITION` is chosen over `MIC` on purpose: it applies the device's
 * noise suppression and automatic gain without the aggressive voice-call
 * processing of `VOICE_COMMUNICATION`, which tends to chew up the quiet
 * fricatives at the ends of words.
 */
class AudioRecorder(
    val sampleRate: Int = SAMPLE_RATE,
    private val frameSamples: Int = FRAME_SAMPLES
) {
    class RecordingFailedException(message: String, cause: Throwable? = null) :
        Exception(message, cause)

    private val running = AtomicBoolean(false)

    val isRecording: Boolean get() = running.get()

    /**
     * Emits fixed-size frames of mono float PCM in the range -1f..1f until the
     * collector stops.
     */
    @SuppressLint("MissingPermission") // Callers hold RECORD_AUDIO; see PermissionScreen.
    fun frames(): Flow<FloatArray> = callbackFlow {
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        if (minBuffer <= 0) {
            close(RecordingFailedException("This device cannot record at ${sampleRate} Hz."))
            return@callbackFlow
        }

        // A generous buffer costs a little latency but prevents dropped frames when
        // the CPU is busy running whisper on the previous chunk.
        val bufferBytes = maxOf(minBuffer, frameSamples * 4 * 8)

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
                bufferBytes
            )
        } catch (error: Throwable) {
            close(RecordingFailedException("Could not open the microphone.", error))
            return@callbackFlow
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            close(RecordingFailedException("The microphone is not available right now."))
            return@callbackFlow
        }

        running.set(true)
        record.startRecording()
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            record.release()
            running.set(false)
            close(RecordingFailedException("Recording did not start. Another app may be using the microphone."))
            return@callbackFlow
        }

        try {
            val buffer = FloatArray(frameSamples)
            while (!isClosedForSend) {
                var filled = 0
                while (filled < frameSamples) {
                    val read = record.read(
                        buffer, filled, frameSamples - filled, AudioRecord.READ_BLOCKING
                    )
                    if (read <= 0) break
                    filled += read
                }
                if (filled <= 0) break
                trySend(if (filled == frameSamples) buffer.copyOf() else buffer.copyOf(filled))
            }
        } catch (error: Throwable) {
            close(RecordingFailedException("Recording stopped unexpectedly.", error))
        }

        awaitClose {
            running.set(false)
            runCatching { record.stop() }
            record.release()
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        const val SAMPLE_RATE = 16_000

        /** 20 ms per frame: fine enough for responsive silence detection. */
        const val FRAME_SAMPLES = 320
    }
}
