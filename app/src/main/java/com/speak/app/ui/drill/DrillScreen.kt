package com.speak.app.ui.drill

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.speak.app.domain.srs.DrillGrade
import com.speak.app.ui.components.BannerTone
import com.speak.app.ui.components.CategoryChip
import com.speak.app.ui.components.EmptyState
import com.speak.app.ui.components.InfoBanner
import com.speak.app.ui.components.MicButton
import com.speak.app.ui.components.PrimaryAction
import com.speak.app.ui.components.SecondaryAction
import com.speak.app.ui.components.SectionCard
import com.speak.app.ui.theme.SpeakIcons

@Composable
fun DrillScreen(viewModel: DrillViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.loading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (state.isFinished) {
        Column(
            modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            EmptyState(
                title = if (state.completedCount > 0) "Done for today" else "Nothing due yet",
                body = if (state.completedCount > 0) {
                    "You reviewed ${state.completedCount} " +
                        if (state.completedCount == 1) "mistake." else "mistakes."
                } else {
                    "Drills are built from your own mistakes. Have a conversation first, " +
                        "and whatever you get wrong will come back here on a schedule."
                }
            )
        }
        return
    }

    val card = state.current ?: return

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "${state.position + 1} of ${state.queue.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        SectionCard {
            Text(
                "You said this before",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                card.promptSentence,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))

            Text(
                "Say it again, correctly",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    card.wrong,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    textDecoration = TextDecoration.LineThrough
                )
                if (state.revealed) {
                    Text(
                        "  →  ",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        card.fixed,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (state.revealed) {
                Spacer(Modifier.height(8.dp))
                Text(
                    card.explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryChip(state.categoryLabel)
                if (card.timesSeen > 1) {
                    Text(
                        "made ${card.timesSeen} times",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        MicButton(
            listening = state.recording,
            level = state.level,
            enabled = true,
            onClick = viewModel::onMicPressed
        )
        Text(
            state.statusLabel,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        state.error?.let {
            InfoBanner(text = it, tone = BannerTone.NEUTRAL)
            Spacer(Modifier.height(12.dp))
        }

        // ---- result of the attempt ----
        val result = state.lastResult
        if (result != null) {
            SectionCard {
                Text(
                    if (result.grade.isPass) "That's it." else "Not quite yet.",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (result.grade.isPass) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when {
                        result.repeatedMistake ->
                            "You used the old wording again, so this one comes back tomorrow."
                        !result.usedFix ->
                            "I didn't hear the corrected part, so this comes back tomorrow."
                        else -> "Scheduled to come back later."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                state.heardText?.let { heard ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Heard: \"$heard\"",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(16.dp))
                PrimaryAction(text = "Next", onClick = viewModel::acceptAutomaticGrade)
                Spacer(Modifier.height(10.dp))
                // The automatic grade can be wrong when the recogniser mishears, so
                // the learner always has the final say on their own recall.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryAction(
                        text = "I got it",
                        onClick = { viewModel.commit(DrillGrade.GOOD) },
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryAction(
                        text = "I missed it",
                        onClick = { viewModel.commit(DrillGrade.MISSED) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SecondaryAction(
                    text = "Show answer",
                    onClick = viewModel::reveal,
                    modifier = Modifier.weight(1f)
                )
                SecondaryAction(
                    text = "Hear it",
                    onClick = viewModel::hearCorrect,
                    modifier = Modifier.weight(1f),
                    icon = SpeakIcons.VolumeUp
                )
            }
            Spacer(Modifier.height(10.dp))
            SecondaryAction(text = "Skip", onClick = viewModel::skip)
        }
        Spacer(Modifier.height(40.dp))
    }
}
