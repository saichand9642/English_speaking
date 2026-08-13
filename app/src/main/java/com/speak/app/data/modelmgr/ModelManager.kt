package com.speak.app.data.modelmgr

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

/**
 * Gets the two model files onto the phone and keeps track of whether they are
 * usable.
 *
 * The speech model is different from the tutor model in an important way: it is
 * baked into the APK, so it only needs copying out of assets, and speech-to-text
 * therefore works on first launch with no network at all. Only the 769 MB tutor
 * model is downloaded, once, with the progress and retry that a download that
 * size demands.
 */
class ModelManager(private val context: Context) {

    sealed interface State {
        data object Checking : State
        data object NotDownloaded : State
        /** [progress] is 0f..1f; [downloadedBytes] and [totalBytes] drive the label. */
        data class Downloading(
            val progress: Float,
            val downloadedBytes: Long,
            val totalBytes: Long
        ) : State
        data object Verifying : State
        data object Ready : State
        data class Failed(val message: String, val canRetry: Boolean = true) : State
    }

    private val _tutorState = MutableStateFlow<State>(State.Checking)
    val tutorState: StateFlow<State> = _tutorState.asStateFlow()

    private val modelsDir: File
        get() = File(context.filesDir, "models").apply { mkdirs() }

    val whisperModelFile: File get() = File(modelsDir, WHISPER_MODEL)
    val tutorModelFile: File get() = File(modelsDir, TUTOR_MODEL)

    val isTutorReady: Boolean
        get() = tutorModelFile.isFile && tutorModelFile.length() == TUTOR_SIZE_BYTES

    /**
     * Copies the bundled speech model out of the APK on first launch.
     *
     * whisper.cpp needs a real file path, and assets inside an APK have none, so
     * one copy is unavoidable. It is left uncompressed in the APK (see
     * `androidResources.noCompress`) which makes this a straight byte copy rather
     * than an inflate.
     */
    suspend fun ensureWhisperModel(): Result<File> = withContext(Dispatchers.IO) {
        val target = whisperModelFile
        runCatching {
            if (target.isFile && target.length() > MIN_PLAUSIBLE_WHISPER_BYTES) return@runCatching target
            val partial = File(modelsDir, "$WHISPER_MODEL.part")
            if (partial.exists()) partial.delete()
            context.assets.open("models/$WHISPER_MODEL").use { input ->
                FileOutputStream(partial).use { output -> input.copyTo(output, BUFFER) }
            }
            if (target.exists()) target.delete()
            if (!partial.renameTo(target)) throw IOException("Could not install the speech model.")
            target
        }
    }

    /** Re-checks the tutor model on disk and updates [tutorState]. */
    suspend fun refreshTutorState() = withContext(Dispatchers.IO) {
        _tutorState.value = if (isTutorReady) State.Ready else State.NotDownloaded
    }

    /**
     * Downloads the tutor model, resuming a part-finished download if one exists.
     *
     * Resuming matters at this size: losing 700 MB of progress to a dropped
     * connection on mobile data is the kind of thing that makes someone give up on
     * an app entirely.
     */
    suspend fun downloadTutorModel() = withContext(Dispatchers.IO) {
        if (isTutorReady) {
            _tutorState.value = State.Ready
            return@withContext
        }

        val target = tutorModelFile
        val partial = File(modelsDir, "$TUTOR_MODEL.part")
        var existing = if (partial.isFile) partial.length() else 0L
        if (existing > TUTOR_SIZE_BYTES) {
            partial.delete()
            existing = 0L
        }

        val free = modelsDir.usableSpace
        if (free < TUTOR_SIZE_BYTES - existing + SPARE_SPACE_BYTES) {
            _tutorState.value = State.Failed(
                "Not enough free space. About ${(TUTOR_SIZE_BYTES / MB)} MB is needed.",
                canRetry = false
            )
            return@withContext
        }

        _tutorState.value = State.Downloading(
            progress = existing.toFloat() / TUTOR_SIZE_BYTES,
            downloadedBytes = existing,
            totalBytes = TUTOR_SIZE_BYTES
        )

        var connection: HttpURLConnection? = null
        try {
            connection = openWithRedirects(TUTOR_URL, existing)
            val code = connection.responseCode

            // 200 to a ranged request means the server ignored the range, so the
            // partial file is meaningless and must be discarded.
            if (existing > 0 && code == HttpURLConnection.HTTP_OK) {
                partial.delete()
                existing = 0L
            }
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                _tutorState.value = State.Failed(describeHttp(code))
                return@withContext
            }

            var downloaded = existing
            connection.inputStream.use { input ->
                FileOutputStream(partial, existing > 0).use { output ->
                    val buffer = ByteArray(BUFFER)
                    var lastReported = 0L
                    while (true) {
                        if (!currentCoroutineContext().isActive) {
                            // Leave the .part file so the next attempt resumes.
                            return@withContext
                        }
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded - lastReported >= PROGRESS_STEP_BYTES) {
                            lastReported = downloaded
                            _tutorState.value = State.Downloading(
                                progress = (downloaded.toFloat() / TUTOR_SIZE_BYTES).coerceIn(0f, 1f),
                                downloadedBytes = downloaded,
                                totalBytes = TUTOR_SIZE_BYTES
                            )
                        }
                    }
                }
            }

            if (downloaded != TUTOR_SIZE_BYTES) {
                _tutorState.value = State.Failed(
                    "The download ended early. Tap retry to resume from where it stopped."
                )
                return@withContext
            }

            _tutorState.value = State.Verifying
            val digest = sha256(partial)
            if (!digest.equals(TUTOR_SHA256, ignoreCase = true)) {
                partial.delete()
                _tutorState.value = State.Failed(
                    "The downloaded file was damaged. Tap retry to download it again."
                )
                return@withContext
            }

            if (target.exists()) target.delete()
            if (!partial.renameTo(target)) {
                _tutorState.value = State.Failed("Could not save the model file.")
                return@withContext
            }
            _tutorState.value = State.Ready
        } catch (error: IOException) {
            _tutorState.value = State.Failed(
                error.message ?: "The download failed. Check your connection and retry."
            )
        } finally {
            connection?.disconnect()
        }
    }

    fun deleteTutorModel(): Boolean {
        File(modelsDir, "$TUTOR_MODEL.part").delete()
        val deleted = tutorModelFile.delete()
        _tutorState.value = State.NotDownloaded
        return deleted
    }

    /** Bytes already fetched by an interrupted download, for the resume label. */
    fun partialBytes(): Long = File(modelsDir, "$TUTOR_MODEL.part").let {
        if (it.isFile) it.length() else 0L
    }

    private fun openWithRedirects(url: String, resumeFrom: Long): HttpURLConnection {
        var current = url
        var redirects = 0
        while (true) {
            val connection = URI(current).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = false
            if (resumeFrom > 0) connection.setRequestProperty("Range", "bytes=$resumeFrom-")
            val code = connection.responseCode
            if (code in 300..399 && redirects < MAX_REDIRECTS) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) throw IOException("The download link is broken.")
                current = location
                redirects++
                continue
            }
            return connection
        }
    }

    private fun describeHttp(code: Int): String = when (code) {
        401, 403 -> "The model host refused the download. Try again later."
        404 -> "The model file has moved. The app needs an update."
        416 -> "The saved partial download is invalid. Tap retry to start again."
        in 500..599 -> "The model host is having trouble. Try again in a few minutes."
        else -> "The download failed (HTTP $code)."
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val WHISPER_MODEL = "ggml-tiny.en-q5_1.bin"
        const val TUTOR_MODEL = "gemma-3-1b-it-Q4_K_M.gguf"

        /**
         * Hosted on ggml-org, which is ungated. The equivalent MediaPipe `.task`
         * build of the same model sits behind a Hugging Face licence gate and
         * returns HTTP 401 to an anonymous request, which is why this app uses
         * llama.cpp with a GGUF file instead.
         */
        const val TUTOR_URL =
            "https://huggingface.co/ggml-org/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf"

        const val TUTOR_SIZE_BYTES = 806_058_240L
        const val TUTOR_SHA256 = "8ccc5cd1f1b3602548715ae25a66ed73fd5dc68a210412eea643eb20eb75a135"

        private const val BUFFER = 1 shl 16
        private const val MB = 1024L * 1024L
        private const val SPARE_SPACE_BYTES = 150L * MB
        private const val MIN_PLAUSIBLE_WHISPER_BYTES = 20L * MB
        private const val PROGRESS_STEP_BYTES = 2L * MB
        private const val MAX_REDIRECTS = 5
    }
}
