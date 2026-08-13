package com.speak.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.speak.app.audio.TtsEngine
import com.speak.app.data.modelmgr.ModelManager
import com.speak.app.data.prefs.SettingsStore
import com.speak.app.di.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: SettingsStore.Settings = SettingsStore.Settings(),
    val tutorState: ModelManager.State = ModelManager.State.Checking,
    val ttsStatus: TtsEngine.Status = TtsEngine.Status.IDLE,
    val voiceDescription: String? = null,
    val maskedKey: String? = null,
    val keyStoreAvailable: Boolean = true,
    val message: String? = null,
    val busy: Boolean = false,
    val nativeInfo: String = ""
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private var downloadJob: Job? = null

    init {
        viewModelScope.launch {
            container.settings.settings.collect { settings ->
                _state.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            container.modelManager.tutorState.collect { tutorState ->
                _state.update { it.copy(tutorState = tutorState) }
            }
        }
        viewModelScope.launch {
            container.ttsEngine.status.collect { status ->
                _state.update {
                    it.copy(
                        ttsStatus = status,
                        voiceDescription = container.ttsEngine.voiceDescription()
                    )
                }
            }
        }
        viewModelScope.launch { container.modelManager.refreshTutorState() }
        _state.update {
            it.copy(
                maskedKey = container.secureKeys.maskedGeminiKey(),
                keyStoreAvailable = container.secureKeys.isAvailable
            )
        }
    }

    // ---- tutor model ----

    fun downloadTutorModel() {
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch { container.modelManager.downloadTutorModel() }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        viewModelScope.launch { container.modelManager.refreshTutorState() }
    }

    fun deleteTutorModel() {
        viewModelScope.launch {
            container.releaseModels()
            container.modelManager.deleteTutorModel()
            _state.update { it.copy(message = "Tutor model removed.") }
        }
    }

    fun partialBytes(): Long = container.modelManager.partialBytes()

    // ---- optional online path ----

    fun saveGeminiKey(key: String) {
        container.secureKeys.setGeminiKey(key)
        _state.update {
            it.copy(
                maskedKey = container.secureKeys.maskedGeminiKey(),
                message = if (key.isBlank()) "Key removed." else "Key saved on this phone only."
            )
        }
        if (key.isBlank()) {
            viewModelScope.launch { container.settings.setPreferOnline(false) }
        }
    }

    fun setPreferOnline(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && !container.secureKeys.hasGeminiKey) {
                _state.update { it.copy(message = "Add a key first.") }
                return@launch
            }
            container.settings.setPreferOnline(enabled)
        }
    }

    fun setGeminiModel(model: String) {
        viewModelScope.launch { container.settings.setGeminiModel(model) }
    }

    // ---- appearance ----

    fun setDarkTheme(setting: SettingsStore.DarkThemeSetting) {
        viewModelScope.launch { container.settings.setDarkTheme(setting) }
    }

    // ---- backup ----

    fun export(destination: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            container.backupManager.export(destination)
                .onSuccess { count ->
                    _state.update { it.copy(busy = false, message = "Exported $count turns.") }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(busy = false, message = error.message ?: "Export failed.")
                    }
                }
        }
    }

    fun import(source: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            container.backupManager.import(source)
                .onSuccess { summary ->
                    _state.update {
                        it.copy(
                            busy = false,
                            message = "Imported ${summary.turns} turns, " +
                                "${summary.mistakes} mistakes and ${summary.drillCards} new drills."
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(busy = false, message = error.message ?: "Import failed.")
                    }
                }
        }
    }

    fun loadNativeInfo() {
        viewModelScope.launch {
            val whisper = runCatching { container.transcriber.systemInfo() }.getOrDefault("")
            _state.update { it.copy(nativeInfo = whisper) }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(container) }
        }
    }
}
