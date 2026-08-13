package com.speak.app.ui.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.speak.app.audio.TtsEngine
import com.speak.app.ui.components.BannerTone
import com.speak.app.ui.components.CorrectionRow
import com.speak.app.ui.components.DiffSentence
import com.speak.app.ui.components.InfoBanner
import com.speak.app.ui.components.MicButton
import com.speak.app.ui.components.PronunciationRow
import com.speak.app.ui.components.SectionCard
import com.speak.app.ui.theme.SpeakIcons

/**
 * The main screen. The microphone is the only thing to press, and the newest
 * exchange sits directly beneath it.
 */
@Composable
fun ConversationScreen(
    viewModel: ConversationViewModel,
    onOpenTtsSettings: () -> Unit,
    onOpenModelDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.loading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(20.dp))
                Text(state.loadingMessage, style = MaterialTheme.typography.bodyLarge)
            }
        }
        return
    }

    state.fatalError?.let { error ->
        Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            InfoBanner(text = error, tone = BannerTone.ERROR)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ---- topic ----
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        state.topic.title.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        state.topic.opener,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                IconButton(onClick = viewModel::nextTopic, modifier = Modifier.size(48.dp)) {
                    Icon(SpeakIcons.Refresh, contentDescription = "Change topic")
                }
            }
        }

        // ---- microphone ----
        item {
            Spacer(Modifier.height(8.dp))
            MicButton(
                listening = state.phase != ConversationPhase.IDLE,
                level = state.level,
                enabled = true,
                onClick = viewModel::onMicPressed
            )
            Text(
                text = phaseLabel(state.phase),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
        }

        // ---- warnings that do not block ----
        if (state.ttsStatus == TtsEngine.Status.NO_VOICE ||
            state.ttsStatus == TtsEngine.Status.FAILED
        ) {
            item {
                InfoBanner(
                    text = "No offline voice is installed, so I can show corrections but not speak them.",
                    tone = BannerTone.WARNING,
                    actionText = "Install a voice",
                    onAction = onOpenTtsSettings
                )
                Spacer(Modifier.height(12.dp))
            }
        } else if (state.ttsStatus == TtsEngine.Status.READY_ONLINE_ONLY) {
            item {
                InfoBanner(
                    text = "The only voice available needs a connection, so speech will stop working offline.",
                    tone = BannerTone.WARNING,
                    actionText = "Install an offline voice",
                    onAction = onOpenTtsSettings
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        if (!state.tutorAvailable && !state.usingOnline) {
            item {
                InfoBanner(
                    text = "The tutor model has not been downloaded, so you will see transcripts but no corrections.",
                    tone = BannerTone.WARNING,
                    actionText = "Download it",
                    onAction = onOpenModelDownload
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        state.transientError?.let { message ->
            item {
                InfoBanner(
                    text = message,
                    tone = BannerTone.NEUTRAL,
                    actionText = "Dismiss",
                    onAction = viewModel::dismissTransientError
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        // ---- reply as it streams in ----
        if (state.streamingReply.isNotBlank() && state.phase != ConversationPhase.IDLE) {
            item {
                SectionCard(title = "Tutor") {
                    Text(state.streamingReply, style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        // ---- completed turns, newest first ----
        items(state.turns) { turn ->
            TurnCard(
                turn = turn,
                onHearSlowly = viewModel::speakSlowly,
                onReplay = viewModel::replayReply
            )
            Spacer(Modifier.height(14.dp))
        }

        if (state.turns.isEmpty() && state.phase == ConversationPhase.IDLE) {
            item {
                Text(
                    "Tap the microphone and answer in two or three sentences. " +
                        "Speak the way you normally would — corrections only help if the mistake was a real one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun TurnCard(
    turn: ConversationTurn,
    onHearSlowly: (String) -> Unit,
    onReplay: (String) -> Unit
) {
    SectionCard {
        // ---- what was said, with the fixes inline ----
        if (turn.diff.isNotEmpty()) {
            DiffSentence(turn.diff)
        } else {
            Text(turn.transcript, style = MaterialTheme.typography.bodyLarge)
        }

        if (turn.wasCorrect) {
            Spacer(Modifier.height(10.dp))
            Text(
                "That was correct.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }

        // ---- corrections ----
        if (turn.corrections.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))
            turn.corrections.forEachIndexed { index, correction ->
                if (index > 0) {
                    Spacer(Modifier.height(14.dp))
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(14.dp))
                }
                CorrectionRow(correction)
            }
        }

        // ---- pronunciation ----
        if (turn.pronunciation.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))
            Text(
                "SOUNDS TO WATCH",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            turn.pronunciation.forEach { note ->
                PronunciationRow(note = note, onHearSlowly = onHearSlowly)
                Spacer(Modifier.height(12.dp))
            }
        }

        // ---- rhythm, only when there was enough speech to judge ----
        turn.rhythm?.verdict?.let { verdict ->
            Spacer(Modifier.height(12.dp))
            Text(
                verdict,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ---- the spoken reply ----
        if (turn.reply.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    turn.reply,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { onReplay(turn.reply) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(SpeakIcons.VolumeUp, contentDescription = "Hear this again")
                }
            }
        }

        // ---- measurements ----
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(
                "${turn.metrics.wordsPerMinute} wpm",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${turn.metrics.wordCount} words",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (turn.metrics.fillerCount > 0) {
                Text(
                    "${turn.metrics.fillerCount} fillers",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun phaseLabel(phase: ConversationPhase): String = when (phase) {
    ConversationPhase.IDLE -> "Tap to speak"
    ConversationPhase.CALIBRATING -> "Listening to the room…"
    ConversationPhase.LISTENING -> "Go ahead, I'm listening"
    ConversationPhase.HEARING -> "I can hear you"
    ConversationPhase.TRANSCRIBING -> "Working out what you said…"
    ConversationPhase.THINKING -> "Thinking…"
    ConversationPhase.REPLYING -> "Speaking"
}
