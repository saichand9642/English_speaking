package com.speak.app.llm

/**
 * Raw JNI surface for llama.cpp. Maps one-to-one onto `llama_jni.cpp`; the usable
 * API is [LocalTutorEngine].
 */
internal class LlamaBridge {

    private external fun nativeLoad(modelPath: String, nCtx: Int, nThreads: Int): Long
    private external fun nativeRelease(handle: Long)
    private external fun nativeBeginTurn(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        seed: Int,
        grammar: String?
    ): Int
    private external fun nativeNextPiece(handle: Long): String?
    private external fun nativeCancel(handle: Long)
    private external fun nativeIsFinished(handle: Long): Boolean
    private external fun nativeSystemInfo(): String

    private var handle: Long = 0L

    val isLoaded: Boolean get() = handle != 0L

    fun load(modelPath: String, contextSize: Int, threads: Int): Boolean {
        if (handle != 0L) release()
        handle = nativeLoad(modelPath, contextSize, threads)
        return handle != 0L
    }

    /** @return prompt tokens consumed, or negative on failure. */
    fun beginTurn(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        seed: Int,
        grammar: String?
    ): Int {
        check(handle != 0L) { "Tutor model is not loaded" }
        return nativeBeginTurn(handle, prompt, maxTokens, temperature, topP, topK, seed, grammar)
    }

    /** @return the next fragment of text, or null when the turn is finished. */
    fun nextPiece(): String? = if (handle == 0L) null else nativeNextPiece(handle)

    fun cancel() {
        if (handle != 0L) nativeCancel(handle)
    }

    fun isFinished(): Boolean = handle == 0L || nativeIsFinished(handle)

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
