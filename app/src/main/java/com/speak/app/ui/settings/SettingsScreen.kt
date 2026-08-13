package com.speak.app.ui.settings

import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.speak.app.audio.TtsEngine
import com.speak.app.data.backup.BackupManager
import com.speak.app.data.modelmgr.ModelManager
import com.speak.app.data.prefs.SettingsStore
import com.speak.app.ui.components.BannerTone
import com.speak.app.ui.components.InfoBanner
import com.speak.app.ui.components.LabelledProgress
import com.speak.app.ui.components.PrimaryAction
import com.speak.app.ui.components.SecondaryAction
import com.speak.app.ui.components.SectionCard

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.loadNativeInfo() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupManager.MIME_TYPE)
    ) { uri -> uri?.let(viewModel::export) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::import) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        state.message?.let { message ->
            InfoBanner(
                text = message,
                tone = BannerTone.NEUTRAL,
                actionText = "Dismiss",
                onAction = viewModel::clearMessage
            )
            Spacer(Modifier.height(14.dp))
        }

        // ---------------- tutor model ----------------
        SectionCard(title = "Tutor model") {
            when (val tutorState = state.tutorState) {
                ModelManager.State.Checking ->
                    Text("Checking…", style = MaterialTheme.typography.bodyMedium)

                ModelManager.State.NotDownloaded -> {
                    Text(
                        "Gemma 3 1B, 769 MB. Downloaded once, then it works with no connection at all. " +
                            "Without it you still get transcripts and pronunciation feedback, but no grammar corrections.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val partial = viewModel.partialBytes()
                    if (partial > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${partial / 1_048_576} MB already downloaded — it will resume.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    PrimaryAction(
                        text = if (partial > 0) "Resume download" else "Download",
                        onClick = viewModel::downloadTutorModel
                    )
                }

                is ModelManager.State.Downloading -> {
                    LabelledProgress(
                        label = "${tutorState.downloadedBytes / 1_048_576} of " +
                            "${tutorState.totalBytes / 1_048_576} MB",
                        progress = tutorState.progress
                    )
                    Spacer(Modifier.height(14.dp))
                    SecondaryAction(text = "Pause", onClick = viewModel::cancelDownload)
                }

                ModelManager.State.Verifying -> {
                    Text(
                        "Checking the download is intact…",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                ModelManager.State.Ready -> {
                    Text(
                        "Ready. Corrections run entirely on this phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(14.dp))
                    SecondaryAction(
                        text = "Remove to free 769 MB",
                        onClick = viewModel::deleteTutorModel
                    )
                }

                is ModelManager.State.Failed -> {
                    Text(
                        tutorState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    if (tutorState.canRetry) {
                        Spacer(Modifier.height(14.dp))
                        PrimaryAction(text = "Retry", onClick = viewModel::downloadTutorModel)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        // ---------------- voice ----------------
        SectionCard(title = "Voice") {
            val (label, tone) = when (state.ttsStatus) {
                TtsEngine.Status.READY_OFFLINE ->
                    "Offline voice ready. Speech works in aeroplane mode." to BannerTone.NEUTRAL
                TtsEngine.Status.READY_ONLINE_ONLY ->
                    "The available voice needs a connection. Install an offline one so speech keeps working offline." to BannerTone.WARNING
                TtsEngine.Status.NO_VOICE ->
                    "No English voice is installed, so I cannot speak yet." to BannerTone.WARNING
                TtsEngine.Status.FAILED ->
                    "The text-to-speech engine would not start." to BannerTone.ERROR
                else -> "Starting…" to BannerTone.NEUTRAL
            }
            Text(label, style = MaterialTheme.typography.bodyMedium)
            state.voiceDescription?.let { description ->
                Spacer(Modifier.height(6.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (tone != BannerTone.NEUTRAL) {
                Spacer(Modifier.height(14.dp))
                SecondaryAction(
                    text = "Install voice data",
                    onClick = {
                        // Opens the system installer for offline voice packages.
                        runCatching {
                            context.startActivity(
                                Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                            )
                        }
                    }
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "In the screen that opens, choose an English voice and download it. " +
                        "Come back here afterwards.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        // ---------------- optional online boost ----------------
        SectionCard(title = "Optional: better feedback online") {
            Text(
                "Entirely optional. Paste a free Google AI Studio key and Speak will use it " +
                    "for sharper corrections when you have a connection. Everything keeps working without one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))

            if (!state.keyStoreAvailable) {
                Text(
                    "Secure storage is unavailable on this device, so the online option is turned off.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                var keyInput by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text(state.maskedKey ?: "API key") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryAction(
                        text = "Save key",
                        onClick = {
                            viewModel.saveGeminiKey(keyInput)
                            keyInput = ""
                        },
                        modifier = Modifier.weight(1f)
                    )
                    if (state.maskedKey != null) {
                        SecondaryAction(
                            text = "Remove",
                            onClick = {
                                viewModel.saveGeminiKey("")
                                keyInput = ""
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Use it when online", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Falls back to the on-device tutor automatically.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.settings.preferOnline,
                        onCheckedChange = viewModel::setPreferOnline
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        // ---------------- appearance ----------------
        SectionCard(title = "Appearance") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsStore.DarkThemeSetting.entries.forEach { option ->
                    FilterChip(
                        selected = state.settings.darkTheme == option,
                        onClick = { viewModel.setDarkTheme(option) },
                        label = {
                            Text(
                                when (option) {
                                    SettingsStore.DarkThemeSetting.SYSTEM -> "System"
                                    SettingsStore.DarkThemeSetting.LIGHT -> "Light"
                                    SettingsStore.DarkThemeSetting.DARK -> "Dark"
                                }
                            )
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        // ---------------- backup ----------------
        SectionCard(title = "Your history") {
            Text(
                "There is no account and no server, so this file is the only way your history " +
                    "survives losing the phone. The API key is never included.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            SecondaryAction(
                text = "Export to a file",
                onClick = { exportLauncher.launch(BackupManager.SUGGESTED_FILENAME) },
                enabled = !state.busy
            )
            Spacer(Modifier.height(10.dp))
            SecondaryAction(
                text = "Import from a file",
                onClick = { importLauncher.launch(arrayOf(BackupManager.MIME_TYPE, "*/*")) },
                enabled = !state.busy
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Importing merges with what is already here; it never overwrites your history.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(14.dp))

        // ---------------- about ----------------
        SectionCard(title = "About") {
            Text(
                "Speak runs speech recognition, the tutor and speech output entirely on this " +
                    "phone. Nothing you say is uploaded unless you turn on the optional online mode.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.nativeInfo.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    state.nativeInfo,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}
