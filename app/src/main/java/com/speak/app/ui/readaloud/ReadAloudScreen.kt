package com.speak.app.ui.readaloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.speak.app.domain.pronunciation.WordScore
import com.speak.app.ui.components.BannerTone
import com.speak.app.ui.components.InfoBanner
import com.speak.app.ui.components.MicButton
import com.speak.app.ui.components.PronunciationRow
import com.speak.app.ui.components.SecondaryAction
import com.speak.app.ui.components.SectionCard
import com.speak.app.ui.components.StatTile
import com.speak.app.ui.theme.SpeakIcons

/**
 * Read-aloud practice.
 *
 * This is the mode where pronunciation feedback is genuinely dependable: because
 * the target sentence is known, a word that comes back different is an observed
 * substitution rather than a guess. It is also the fastest mode, since scoring is
 * pure alignment and needs no language model at all.
 */
@Composable
fun ReadAloudScreen(
    viewModel: ReadAloudViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            state.sentence.focus.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        SectionCard {
            if (state.scores.isEmpty()) {
                Text(
                    state.sentence.text,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                ScoredSentence(state.scores)
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SecondaryAction(
                text = "Hear it",
                onClick = viewModel::hearSentence,
                modifier = Modifier.weight(1f),
                icon = SpeakIcons.VolumeUp
            )
            SecondaryAction(
                text = "Next",
                onClick = viewModel::nextSentence,
                modifier = Modifier.weight(1f),
                icon = SpeakIcons.SkipNext
            )
        }

        Spacer(Modifier.height(8.dp))
        MicButton(
            listening = state.recording,
            level = state.level,
            enabled = !state.loading,
            onClick = viewModel::onMicPressed
        )
        Text(
            text = state.statusLabel,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        state.error?.let {
            InfoBanner(text = it, tone = BannerTone.NEUTRAL)
            Spacer(Modifier.height(12.dp))
        }

        if (state.scores.isNotEmpty()) {
            SectionCard(title = "Result") {
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    StatTile(label = "Words clear", value = "${state.accuracyPercent}%")
                    state.rhythm?.let { rhythm ->
                        StatTile(
                            label = "Rhythm",
                            value = if (rhythm.isSyllableTimed) "Even" else "Varied",
                            caption = "nPVI ${rhythm.npvi.toInt()}"
                        )
                    }
                }
                state.rhythm?.verdict?.let { verdict ->
                    Spacer(Modifier.height(14.dp))
                    Text(
                        verdict,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (state.notes.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(16.dp))
                    state.notes.forEach { note ->
                        PronunciationRow(note = note, onHearSlowly = viewModel::speakSlowly)
                        Spacer(Modifier.height(14.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Heard: \"${state.heardText}\"",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(40.dp))
    }
}

/** The target sentence, coloured word by word by how it came out. */
@Composable
private fun ScoredSentence(scores: List<WordScore>) {
    val correct = MaterialTheme.colorScheme.onSurface
    val unclear = MaterialTheme.colorScheme.tertiary
    val wrong = MaterialTheme.colorScheme.error
    val missing = MaterialTheme.colorScheme.onSurfaceVariant

    Text(
        text = buildAnnotatedString {
            scores.forEachIndexed { index, score ->
                if (index > 0) append(" ")
                val style = when (score.outcome) {
                    WordScore.Outcome.CORRECT -> SpanStyle(color = correct)
                    WordScore.Outcome.UNCLEAR ->
                        SpanStyle(color = unclear, fontWeight = FontWeight.SemiBold)
                    WordScore.Outcome.SUBSTITUTED ->
                        SpanStyle(color = wrong, fontWeight = FontWeight.SemiBold)
                    WordScore.Outcome.MISSING ->
                        SpanStyle(color = missing, textDecoration = TextDecoration.LineThrough)
                }
                withStyle(style) { append(score.expected) }
            }
        },
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}
