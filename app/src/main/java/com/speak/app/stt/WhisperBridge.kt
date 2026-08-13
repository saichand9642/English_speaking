package com.speak.app.stt

/**
 * Raw JNI surface for whisper.cpp. Everything here maps one-to-one onto
 * `whisper_jni.cpp`; the usable API is [WhisperTranscriber].
 */
internal class WhisperBridge {

    private external fun nativeInit(modelPath: String): Long
    private external fun nativeRelease(handle: Long)
    private external fun nativeTranscribe(handle: Long, samples: FloatArray, threads: Int): String
    private external fun nativeSystemInfo(): String

    private var handle: Long = 0L

    val isLoaded: Boolean get() = handle != 0L

    /** @return true when the model loaded. */
    fun load(modelPath: String): Boolean {
        if (handle != 0L) release()
        handle = nativeInit(modelPath)
        return handle != 0L
    }

    /** Returns the raw JSON document described in `whisper_jni.cpp`. */
    fun transcribeToJson(samples: FloatArray, threads: Int): String {
        check(handle != 0L) { "Whisper model is not loaded" }
        return nativeTranscribe(handle, samples, threads)
    }

    fun systemInfo(): String = nativeSystemInfo()

    fun release() {
        if (handle != 0L) {
            nativeRelease(handle)
            handle = 0L
        }
    }

    companion object {
        @Volatile
        private var libraryLoaded = false

        @Synchronized
        fun ensureLibraryLoaded() {
            if (!libraryLoaded) {
                System.loadLibrary("speak_native")
                libraryLoaded = true
            }
        }
    }
}
