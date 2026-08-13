package com.speak.app.di

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.speak.app.audio.TtsEngine
import com.speak.app.audio.TurnRecorder
import com.speak.app.data.backup.BackupManager
import com.speak.app.data.db.SpeakDatabase
import com.speak.app.data.modelmgr.ModelManager
import com.speak.app.data.prefs.SecureKeyStore
import com.speak.app.data.prefs.SettingsStore
import com.speak.app.data.repo.PracticeRepository
import com.speak.app.domain.tutor.TutorEngine
import com.speak.app.llm.GeminiTutorEngine
import com.speak.app.llm.LocalTutorEngine
import com.speak.app.stt.WhisperTranscriber
import kotlinx.coroutines.sync.withLock

/**
 * Manual dependency container.
 *
 * Hand-wiring rather than Hilt: this is a single-user app with one Activity and a
 * shallow graph, and skipping an annotation processor keeps the build simple and
 * fast. Everything below is created once and shared.
 */
class AppContainer(private val context: Context) {

    val database: SpeakDatabase by lazy { SpeakDatabase.get(context) }
    val repository: PracticeRepository by lazy { PracticeRepository(database) }
    val settings: SettingsStore by lazy { SettingsStore(context) }
    val secureKeys: SecureKeyStore by lazy { SecureKeyStore(context) }
    val modelManager: ModelManager by lazy { ModelManager(context) }
    val backupManager: BackupManager by lazy { BackupManager(context, database) }

    val transcriber: WhisperTranscriber by lazy { WhisperTranscriber() }
    val ttsEngine: TtsEngine by lazy { TtsEngine(context) }

    fun newTurnRecorder(): TurnRecorder = TurnRecorder()

    @Volatile
    private var localEngine: LocalTutorEngine? = null

    /**
     * The offline tutor, created once the model file exists. Null means the model
     * has not been downloaded yet.
     */
    fun localTutorEngine(): LocalTutorEngine? {
        if (!modelManager.isTutorReady) return null
        return localEngine ?: synchronized(this) {
            localEngine ?: LocalTutorEngine(
                modelPath = modelManager.tutorModelFile.absolutePath
            ).also { localEngine = it }
        }
    }

    fun geminiEngine(modelId: String): TutorEngine = GeminiTutorEngine(
        apiKeyProvider = { secureKeys.geminiKey() },
        modelId = modelId,
        connectivity = ::hasInternet
    )

    private val loadLock = kotlinx.coroutines.sync.Mutex()

    /**
     * Copies the bundled speech model out of assets and loads it.
     *
     * Guarded by a mutex because three different screens can ask for the
     * transcriber, and loading a 31 MB model twice concurrently would waste both
     * time and memory on a phone that has little of either to spare.
     */
    suspend fun ensureTranscriberReady(): Result<Unit> = loadLock.withLock {
        if (transcriber.isReady) return@withLock Result.success(Unit)
        val model = modelManager.ensureWhisperModel().getOrElse {
            return@withLock Result.failure(it)
        }
        if (transcriber.load(model.absolutePath)) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("The speech model could not be loaded."))
        }
    }

    /** Loads the tutor model, which must already have been downloaded. */
    suspend fun ensureTutorReady(): Result<Unit> = loadLock.withLock {
        val engine = localTutorEngine()
            ?: return@withLock Result.failure(
                IllegalStateException("The tutor model has not been downloaded yet.")
            )
        if (engine.isLoaded) return@withLock Result.success(Unit)
        if (engine.load()) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("The tutor model could not be loaded."))
        }
    }

    fun hasInternet(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun releaseModels() {
        localEngine?.release()
        localEngine = null
        transcriber.release()
    }
}
